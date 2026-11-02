#!/usr/bin/env python3
"""Quality-aware replay of the bounded Station 3 color observation.

Raw source files are immutable. This script writes only derived artifacts and
abstains by name whenever a predeclared evidence-quality check fails.
"""

from __future__ import annotations

import csv
import hashlib
import json
import math
from collections import Counter, defaultdict
from pathlib import Path

import cv2
import numpy as np

from observation_falsifier import (
    DATA,
    LABELS,
    SOURCE_DRIVE_OBJECT_ID,
    VIDEO,
    canonical_hash,
    delta_e_2000,
    load_requested_frames,
    make_mask,
    sha256,
)


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "artifacts" / "observation_quality_falsifier"
CONFIG_PATH = Path(__file__).with_name("observation_quality_falsifier_config.json")
SCRIPT_PATH = Path(__file__)


def lab_from_pixels(image_bgr: np.ndarray, mask: np.ndarray) -> np.ndarray:
    converted = cv2.cvtColor(image_bgr, cv2.COLOR_BGR2LAB).astype(np.float64)
    median = np.median(converted[mask > 0], axis=0)
    return np.array([median[0] * 100.0 / 255.0, median[1] - 128.0, median[2] - 128.0])


def white_reference_metrics(image: np.ndarray, config: dict) -> tuple[np.ndarray, dict]:
    x1, y1, x2, y2 = config["white_reference_xyxy"]
    patch = image[y1:y2, x1:x2]
    pixels = patch.reshape(-1, 3)
    medians = np.median(pixels, axis=0)
    target = float(np.mean(medians))
    gains = np.clip(
        target / np.maximum(medians, 1.0),
        config["white_balance_gain_limits"][0],
        config["white_balance_gain_limits"][1],
    )
    balanced = np.clip(image.astype(np.float32) * gains.reshape(1, 1, 3), 0, 255).astype(np.uint8)
    quality = config["white_reference_quality"]
    clip_fraction = float(np.mean(np.any((pixels <= 3) | (pixels >= 252), axis=1)))
    reasons = []
    if float(np.min(medians)) < quality["minimum_median_channel"]:
        reasons.append("white_reference_too_dark")
    if float(np.max(medians)) > quality["maximum_median_channel"]:
        reasons.append("white_reference_too_bright")
    if float(np.ptp(medians)) > quality["maximum_channel_median_spread"]:
        reasons.append("white_reference_not_neutral")
    if clip_fraction > quality["maximum_clip_fraction"]:
        reasons.append("white_reference_clipped")
    return balanced, {
        "white_reference_channel_medians_bgr": [round(float(v), 6) for v in medians],
        "white_reference_channel_spread": round(float(np.ptp(medians)), 6),
        "white_reference_clip_fraction": round(clip_fraction, 8),
        "white_balance_gains": [round(float(v), 6) for v in gains],
        "white_reference_reasons": reasons,
    }


def frame_quality(image: np.ndarray, balanced: np.ndarray, mask: np.ndarray, config: dict) -> dict:
    pixels = balanced[mask > 0]
    hsv = cv2.cvtColor(balanced, cv2.COLOR_BGR2HSV)[mask > 0]
    occ = config["liquid_occupancy"]
    occupied = (hsv[:, 1] >= occ["minimum_hsv_saturation"]) | (hsv[:, 2] <= occ["maximum_dark_value"])
    occupancy_fraction = float(np.mean(occupied))
    occupied_image = np.zeros(mask.shape, dtype=np.uint8)
    occupied_image[mask > 0] = occupied.astype(np.uint8)
    component_count, _, stats, _ = cv2.connectedComponentsWithStats(occupied_image, 8)
    largest_component_fraction = 0.0 if component_count <= 1 else float(
        np.max(stats[1:, cv2.CC_STAT_AREA]) / np.count_nonzero(mask)
    )
    highlight_clip_fraction = float(np.mean(np.any(pixels >= 252, axis=1)))
    x1, y1, x2, y2 = config["focus_crop_xyxy"]
    original_gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    focus = float(cv2.Laplacian(original_gray[y1:y2, x1:x2], cv2.CV_64F).var())
    low, high = config["occlusion_proxy"]["canny_thresholds"]
    edges = cv2.Canny(original_gray, low, high)
    internal_edge_fraction = float(np.mean(edges[mask > 0] > 0))
    sobel_x = np.abs(cv2.Sobel(original_gray, cv2.CV_32F, 1, 0, ksize=3))
    vial = config["vial_presence_proxy"]
    lx1, ly1, lx2, ly2 = vial["left_wall_strip_xyxy"]
    rx1, ry1, rx2, ry2 = vial["right_wall_strip_xyxy"]
    left_wall = float(np.percentile(sobel_x[ly1:ly2, lx1:lx2], 75))
    right_wall = float(np.percentile(sobel_x[ry1:ry2, rx1:rx2], 75))
    reasons = []
    if occupancy_fraction < occ["minimum_fraction"]:
        reasons.append("liquid_occupancy_proxy_low")
    if left_wall < vial["minimum_left_wall_sobel_x_p75"] or right_wall < vial["minimum_right_wall_sobel_x_p75"]:
        reasons.append("vial_wall_presence_proxy_low")
    oq = config["occlusion_proxy"]
    if largest_component_fraction < oq["minimum_largest_occupancy_component_fraction"]:
        reasons.append("liquid_region_fragmented_or_occluded")
    if internal_edge_fraction > oq["maximum_internal_canny_edge_fraction"]:
        reasons.append("liquid_region_edge_obstruction_high")
    iq = config["image_quality"]
    if highlight_clip_fraction > iq["maximum_highlight_clip_fraction"]:
        reasons.append("liquid_roi_highlight_clipped")
    if focus < iq["minimum_focus_laplacian_variance"]:
        reasons.append("vial_region_out_of_focus")
    return {
        "liquid_occupancy_fraction": round(occupancy_fraction, 8),
        "largest_occupancy_component_fraction": round(largest_component_fraction, 8),
        "internal_canny_edge_fraction": round(internal_edge_fraction, 8),
        "left_vial_wall_sobel_x_p75": round(left_wall, 6),
        "right_vial_wall_sobel_x_p75": round(right_wall, 6),
        "highlight_clip_fraction": round(highlight_clip_fraction, 8),
        "focus_laplacian_variance": round(focus, 6),
        "frame_quality_reasons": reasons,
    }


def observe_window(frames: dict[int, np.ndarray], end_s: float, timing_shift: float,
                   morph_px: int, config: dict) -> dict:
    start_before_end, end_before_end = config["pre_end_window_seconds"]
    times = np.linspace(
        end_s - start_before_end + timing_shift,
        end_s - end_before_end + timing_shift,
        config["window_sample_count"],
    )
    mask = make_mask((480, 640), config["liquid_mask_polygon_xy"], morph_px)
    frame_receipts, labs, reasons = [], [], []
    if int(np.count_nonzero(mask)) < config["minimum_mask_pixels"]:
        reasons.append("geometry_mask_too_small")
    for sample_time in times:
        frame_id = int(round(float(sample_time) * config["fps"]))
        image = frames.get(frame_id)
        if image is None:
            reasons.append(f"frame_unreadable:{frame_id}")
            continue
        balanced, white = white_reference_metrics(image, config)
        quality = frame_quality(image, balanced, mask, config)
        reasons.extend(white.pop("white_reference_reasons"))
        reasons.extend(quality.pop("frame_quality_reasons"))
        lab = lab_from_pixels(balanced, mask)
        labs.append(lab)
        frame_receipts.append({
            "frame_id": frame_id,
            "sample_time_video_s": round(float(sample_time), 6),
            "lab": [round(float(v), 6) for v in lab],
            **white,
            **quality,
        })
    temporal_max = math.nan
    median_lab = np.array([math.nan] * 3)
    if len(labs) == config["window_sample_count"]:
        median_lab = np.median(np.stack(labs), axis=0)
        temporal_max = max(delta_e_2000(value, median_lab) for value in labs)
        if temporal_max > config["image_quality"]["maximum_window_delta_e00_from_median"]:
            reasons.append("liquid_color_temporally_unstable")
    else:
        reasons.append(f"incomplete_window:{len(labs)}/{config['window_sample_count']}")
    reasons = sorted(set(reasons))
    return {
        "evidence_status": "pass" if not reasons else "abstain",
        "abstention_reasons": reasons,
        "lab": median_lab,
        "temporal_max_delta_e00": temporal_max,
        "mask_pixels": int(np.count_nonzero(mask)),
        "frame_receipts": frame_receipts,
    }


def write_geometry_receipts(rows: list[dict], frames: dict[int, np.ndarray], config: dict) -> None:
    overlay_dir = OUT / "geometry_overlays"
    overlay_dir.mkdir(parents=True, exist_ok=True)
    thumbs = []
    polygon = np.asarray(config["liquid_mask_polygon_xy"], dtype=np.int32)
    x1, y1, x2, y2 = config["white_reference_xyxy"]
    for row in rows:
        vial = int(row["vial"])
        frame_id = int(round((float(row["end_s"]) - 1.0) * config["fps"]))
        image = frames.get(frame_id)
        if image is None:
            continue
        overlay = image.copy()
        cv2.polylines(overlay, [polygon], True, (0, 255, 0), 2)
        cv2.rectangle(overlay, (x1, y1), (x2, y2), (255, 0, 255), 2)
        cv2.putText(overlay, f"vial {vial} recipe {row['recipe_id']} frame {frame_id}",
                    (10, 28), cv2.FONT_HERSHEY_SIMPLEX, 0.62, (0, 255, 255), 2)
        cv2.imwrite(str(overlay_dir / f"vial_{vial:02d}_geometry.jpg"), overlay)
        thumbs.append(cv2.resize(overlay, (320, 240)))
    if thumbs:
        while len(thumbs) % 5:
            thumbs.append(np.zeros_like(thumbs[0]))
        sheet = np.vstack([np.hstack(thumbs[i:i + 5]) for i in range(0, len(thumbs), 5)])
        cv2.imwrite(str(OUT / "all_30_geometry_contact_sheet.jpg"), sheet)


def main() -> None:
    config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
    rows = list(csv.DictReader(LABELS.open(newline="", encoding="utf-8")))
    if len(rows) != 30:
        raise RuntimeError(f"expected 30 labeled vials, found {len(rows)}")
    OUT.mkdir(parents=True, exist_ok=True)
    source_hashes = {"video_sha256": sha256(VIDEO), "labels_sha256": sha256(LABELS)}
    config_hash = canonical_hash(config)
    code_hash = sha256(SCRIPT_PATH)

    requested: set[int] = set()
    start_before_end, end_before_end = config["pre_end_window_seconds"]
    for row in rows:
        end_s = float(row["end_s"])
        for shift in config["timing_shifts_seconds"]:
            times = np.linspace(end_s - start_before_end + shift, end_s - end_before_end + shift,
                                config["window_sample_count"])
            requested.update(int(round(float(t) * config["fps"])) for t in times)
        requested.add(int(round((end_s - 1.0) * config["fps"])))
    cap = cv2.VideoCapture(str(VIDEO))
    if not cap.isOpened():
        raise RuntimeError(f"cannot open {VIDEO}")
    frames = load_requested_frames(cap, requested)
    cap.release()
    write_geometry_receipts(rows, frames, config)

    observations = []
    for row in rows:
        for shift in config["timing_shifts_seconds"]:
            for morph_px in config["mask_morphology_pixels"]:
                obs = observe_window(frames, float(row["end_s"]), shift, morph_px, config)
                lab = obs.pop("lab")
                observations.append({
                    "schema": config["schema"],
                    "source_drive_object_id": SOURCE_DRIVE_OBJECT_ID,
                    "source_video_sha256": source_hashes["video_sha256"],
                    "source_labels_sha256": source_hashes["labels_sha256"],
                    "configuration_sha256": config_hash,
                    "code_version_sha256": code_hash,
                    "vial": int(row["vial"]),
                    "recipe_id": int(row["recipe_id"]),
                    "decision_time_video_s": float(row["end_s"]),
                    "sampling_semantics": "pre_end_interval; not proven post-fill settled",
                    "timing_shift_s": shift,
                    "mask_morphology_px": morph_px,
                    "mask_pixels": obs["mask_pixels"],
                    "lab_l": round(float(lab[0]), 6),
                    "lab_a": round(float(lab[1]), 6),
                    "lab_b": round(float(lab[2]), 6),
                    "temporal_max_delta_e00": round(float(obs["temporal_max_delta_e00"]), 6),
                    "evidence_status": obs["evidence_status"],
                    "abstention_reasons": json.dumps(obs["abstention_reasons"]),
                    "eligible_reference_count": 0,
                    "prospective_evidence_status": "insufficient reference evidence",
                    "frame_receipts": json.dumps(obs["frame_receipts"], separators=(",", ":")),
                })

    with (OUT / "observations.csv").open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(observations[0]))
        writer.writeheader()
        writer.writerows(observations)

    nominal = {int(o["vial"]): o for o in observations
               if float(o["timing_shift_s"]) == 0.0 and int(o["mask_morphology_px"]) == 0}
    recipe_results = []
    for recipe in range(5, 15):
        vials = [1 + (recipe - 5) * 3 + offset for offset in range(3)]
        selected = [nominal[v] for v in vials]
        if any(item["evidence_status"] != "pass" for item in selected):
            recipe_results.append({"recipe_id": recipe, "vials": vials, "status": "abstain",
                                   "reasons": sorted(set(sum((json.loads(x["abstention_reasons"]) for x in selected), [])))})
            continue
        labs = [np.array([x["lab_l"], x["lab_a"], x["lab_b"]]) for x in selected]
        recipe_results.append({
            "recipe_id": recipe,
            "vials": vials,
            "status": "retrospective_measurement_eligible",
            "first_to_later_delta_e00": round(delta_e_2000(labs[0], (labs[1] + labs[2]) / 2), 6),
            "later_pair_delta_e00": round(delta_e_2000(labs[1], labs[2]), 6),
            "interpretation": "documented_positive" if recipe == 7 else "unlabeled_control_only",
        })

    by_variant: dict[tuple[float, int], dict[int, dict]] = defaultdict(dict)
    for item in observations:
        by_variant[(float(item["timing_shift_s"]), int(item["mask_morphology_px"]))][int(item["vial"])] = item
    recipe7_perturbations = []
    for (shift, morph_px), items in sorted(by_variant.items()):
        selected = [items[v] for v in (7, 8, 9)]
        if any(item["evidence_status"] != "pass" for item in selected):
            recipe7_perturbations.append({
                "timing_shift_s": shift,
                "mask_morphology_px": morph_px,
                "status": "abstain",
                "reasons": sorted(set(sum((json.loads(x["abstention_reasons"]) for x in selected), []))),
            })
            continue
        labs = [np.array([x["lab_l"], x["lab_a"], x["lab_b"]]) for x in selected]
        later = (labs[1] + labs[2]) / 2.0
        recipe7_perturbations.append({
            "timing_shift_s": shift,
            "mask_morphology_px": morph_px,
            "status": "retrospective_measurement_eligible",
            "vial7_to_later_delta_e00": round(delta_e_2000(labs[0], later), 6),
            "vial8_to_vial9_delta_e00": round(delta_e_2000(labs[1], labs[2]), 6),
            "direction_lab_vial7_minus_later": [round(float(v), 6) for v in labs[0] - later],
        })

    reasons = Counter(reason for item in observations for reason in json.loads(item["abstention_reasons"]))
    receipts = [receipt for item in observations for receipt in json.loads(item["frame_receipts"])]
    metric_ranges = {}
    for name in ("liquid_occupancy_fraction", "largest_occupancy_component_fraction",
                 "internal_canny_edge_fraction", "left_vial_wall_sobel_x_p75",
                 "right_vial_wall_sobel_x_p75", "highlight_clip_fraction",
                 "focus_laplacian_variance", "white_reference_clip_fraction",
                 "white_reference_channel_spread"):
        values = [float(receipt[name]) for receipt in receipts]
        metric_ranges[name] = {"minimum": min(values), "maximum": max(values)}
    temporal_values = [float(item["temporal_max_delta_e00"]) for item in observations]
    metric_ranges["temporal_max_delta_e00"] = {
        "minimum": min(temporal_values),
        "maximum": max(temporal_values),
    }
    summary = {
        "schema": config["schema"],
        "status": "quality_gate_complete",
        "claim_ceiling": "retrospective color observation only; no causal, production, or prospective claim",
        "sampling_semantics": "all windows occur before logged fill end; post-fill settling is not established",
        "source_hashes": source_hashes,
        "configuration_sha256": config_hash,
        "code_version_sha256": code_hash,
        "geometry_provenance": config["geometry_provenance"],
        "predeclared_thresholds": {
            "liquid_occupancy": config["liquid_occupancy"],
            "vial_presence_proxy": config["vial_presence_proxy"],
            "occlusion_proxy": config["occlusion_proxy"],
            "image_quality": config["image_quality"],
            "white_reference_quality": config["white_reference_quality"],
        },
        "observed_quality_metric_ranges": metric_ranges,
        "labeled_vials": len(rows),
        "derived_rows": len(observations),
        "pass_rows": sum(x["evidence_status"] == "pass" for x in observations),
        "abstention_rows": sum(x["evidence_status"] != "pass" for x in observations),
        "abstention_reason_counts": dict(sorted(reasons.items())),
        "recipe_first_vs_later_nominal": recipe_results,
        "recipe7_quality_passing_perturbations": recipe7_perturbations,
        "prospective_reference_disposition": "zero eligible references; every target remains insufficient evidence prospectively",
        "geometry_receipts": {
            "per_vial_overlays": 30,
            "contact_sheet": "all_30_geometry_contact_sheet.jpg",
            "polygon_xy": config["liquid_mask_polygon_xy"],
            "white_reference_xyxy": config["white_reference_xyxy"],
        },
    }
    (OUT / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")

    recipe7 = next(item for item in recipe_results if item["recipe_id"] == 7)
    passing_perturbations = [item for item in recipe7_perturbations
                             if item["status"] == "retrospective_measurement_eligible"]
    lines = [
        "# station 3 quality-aware observation falsifier",
        "",
        "status: quality gate complete; prospective claim remains insufficient evidence",
        "",
        "## evidence boundary",
        "",
        "- all sample windows occur 1.5 to 0.5 seconds before logged fill end, with timing perturbations of -0.5, 0, and +0.5 seconds",
        "- these are pre-end windows; they are not described as settled or post-fill observations",
        "- each frame is checked for liquid occupancy and continuity, expected vial-wall gradients, internal edge obstruction, highlight clipping, local focus, and white-reference validity",
        "- each five-frame window is checked for temporal color stability",
        "- all 30 nominal windows have saved geometry overlays and one contact sheet",
        "- the fixed polygon and wall checks are bounded image proxies, not a learned vial detector; physical vial identity is not independently proven",
        "",
        "## result",
        "",
        f"- derived rows: {summary['derived_rows']}",
        f"- pass rows: {summary['pass_rows']}",
        f"- abstention rows: {summary['abstention_rows']}",
        f"- abstention reasons: `{json.dumps(summary['abstention_reason_counts'], sort_keys=True)}`",
        f"- recipe 7 nominal disposition: `{recipe7['status']}`",
    ]
    if recipe7["status"] == "retrospective_measurement_eligible":
        lines.extend([
            f"- recipe 7 vial 7 versus later vials 8 and 9: {recipe7['first_to_later_delta_e00']:.2f} delta e00",
            f"- recipe 7 later-vial pair: {recipe7['later_pair_delta_e00']:.2f} delta e00",
            f"- quality-passing timing and mask perturbations: {len(passing_perturbations)}/{len(recipe7_perturbations)}",
            f"- recipe 7 first-to-later perturbation range: {min(x['vial7_to_later_delta_e00'] for x in passing_perturbations):.2f} to {max(x['vial7_to_later_delta_e00'] for x in passing_perturbations):.2f} delta e00",
        ])
    lines.extend([
        "- prospective references available at vial decision time: 0",
        "",
        "the measured recipe 7 direction may be retained only as a retrospective observation when its quality gate passes. recipes 6, 12, and 14 remain necessary counterexamples to any transition-specific or causal interpretation. no row is promoted to a production diagnosis.",
        "",
        "## replay",
        "",
        f"- source video sha-256: `{source_hashes['video_sha256']}`",
        f"- source labels sha-256: `{source_hashes['labels_sha256']}`",
        f"- configuration sha-256: `{config_hash}`",
        f"- code version sha-256: `{code_hash}`",
        "- command: `python3 analysis/observation_quality_falsifier.py`",
    ])
    (OUT / "report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(json.dumps({k: summary[k] for k in ("derived_rows", "pass_rows", "abstention_rows", "abstention_reason_counts")}, indent=2))
    print(json.dumps(recipe7, indent=2))


if __name__ == "__main__":
    main()
