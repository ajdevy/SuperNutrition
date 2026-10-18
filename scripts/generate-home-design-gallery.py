#!/usr/bin/env python3
"""Regenerate the Russian Home review gallery from immutable local captures.

Uses only the Python standard library. Does not capture, inspect, resize, or
modify screenshots, acknowledge the inspection journal, or execute devices,
builds, Git, or network commands. Only derived gallery outputs are written.
"""
from __future__ import annotations

import argparse
import csv
import datetime as dt
import hashlib
import html
import io
import json
import re
import struct
from collections import Counter
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import quote, unquote

REPOSITORY = Path(__file__).resolve().parents[1]
DEFAULT_DIRECTORY = REPOSITORY / "_bmad-output/design-review/home-no-scroll-2026-10-17"
STATUS_LABELS = {
    "fit": "Помещается без прокрутки",
    "needs-designer": "Нужен дизайнер",
    "missing": "Снимка пока нет",
    "pending": "Ожидает проверки",
}
TAG_LABELS = {
    "home_date": "Дата", "home_profile": "Профиль",
    "home_add_food": "Добавить еду", "home_view_goals": "Посмотреть цели",
    "home_summary": "Панель ритма", "home_metric_fiber": "Клетчатка",
    "home_metric_water": "Вода", "home_metric_protein": "Белок",
    "home_metric_movement": "Движение", "home_next_step": "Полезный шаг",
}
TEXT_LABELS = {
    "Super Nutrition": "Название приложения", "6 day rhythm": "Значок ритма",
    "What kind of care do you need today?": "Вопрос о заботе",
    "Add a meal": "Добавить еду", "View goals": "Посмотреть цели",
    "Today’s rhythm": "Заголовок панели ритма",
    "You’re building a good day": "Подзаголовок панели ритма",
    "Fiber": "Клетчатка", "12 / 25 g": "Значение клетчатки",
    "Water": "Вода", "1.2 / 2 L": "Значение воды",
    "Protein": "Белок", "72 / 90 g": "Значение белка",
    "Movement": "Движение", "32 / 45 min": "Значение движения",
    "Your next helpful step": "Заголовок полезного шага",
    "Add one leafy green to dinner": "Описание полезного шага",
    "Do it": "Действие полезного шага",
}
# These five original PNGs have explicit, digest-matched visual findings in
# the inspection journal. Do not extrapolate the defect to uninspected AVDs.
REWARDS_NAVIGATION_FINDING_GEOMETRIES = frozenset({
    "320x480-160dpi", "480x800-160dpi", "480x854-160dpi",
    "1024x600-160dpi", "1280x800-160dpi",
})
REWARDS_NAVIGATION_FINDING_REASON = (
    "Подпись вкладки Rewards переносится и обрезается при 200% системного текста. "
    "Нужно изменить размещение/ширину подписи навигации, сохранив размер текста."
)
DIAGNOSTIC_VERIFIER_CORRECTION_REASON = (
    "Старый capture-verifier ошибочно записал native_initial_fit=false: он искал "
    "кликабельный узел с подписью Do it, тогда как подпись — некликабельный дочерний "
    "узел внутри кликабельного родителя с той же рамкой 48 dp. Перепроверка в "
    "classification-audit.json и свежий просмотр исходного PNG подтверждают полный "
    "кадр и действие выше навигации. Исходные метаданные не исправляются; это "
    "историческая диагностика другого APK, исключённая из итоговых 100 случаев."
)


def escape(value: object) -> str:
    return html.escape(str(value), quote=True)


def link(path: str, label: str, original: bool = False) -> str:
    target = ' target="_blank" rel="noopener"' if original else ""
    return f'<a href="{escape(quote(path, safe="/#"))}"{target}>{escape(label)}</a>'


def number(value: float | None) -> str:
    if value is None:
        return "не записано"
    return f"{value:.2f}".rstrip("0").rstrip(".")


def dimensions(width: float | None, height: float | None) -> str:
    if width is None or height is None:
        return "не записано"
    return f"{number(width)} × {number(height)}"


def orientation(width: float | None, height: float | None) -> str:
    if width is None or height is None:
        return "не записана"
    return "портрет" if width < height else "альбом" if width > height else "квадрат"


def read_json(path: Path) -> object:
    return json.loads(path.read_text(encoding="utf-8"))


def geometry_from_key(key: str) -> dict:
    match = re.fullmatch(r"(\d+)x(\d+)-(\d+)dpi", key)
    if not match:
        raise ValueError(f"Invalid geometry in inventory: {key}")
    width, height, density = map(int, match.groups())
    if min(width, height, density) <= 0:
        raise ValueError(f"Non-positive geometry in inventory: {key}")
    return {
        "width_px": width, "height_px": height, "density_dpi": density,
        "width_dp": width * 160 / density, "height_dp": height * 160 / density,
    }


def load_inspections(directory: Path, warnings: list[str]) -> dict[str, list[dict]]:
    path = directory / "visual-inspection.jsonl"
    records: dict[str, list[dict]] = {}
    if not path.exists():
        return records
    for index, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        try:
            entry = json.loads(line)
            relative = Path(entry["png"])
            if relative.is_absolute():
                relative = relative.relative_to(directory)
            if ".." in relative.parts:
                raise ValueError("inspection path escapes its evidence directory")
            records.setdefault(relative.as_posix(), []).append(entry)
        except (ValueError, KeyError, TypeError) as error:
            warnings.append(f"Строка журнала {index} не прочитана: {error}")
    return records


def effective_density(metadata: dict) -> int | None:
    readings = re.findall(r"(?:Physical|Override) density:\s*(\d+)", metadata.get("wm_density", ""))
    return int(readings[-1]) if readings else None


def capture_record(directory: Path, leaf: Path, requested: dict, font: float,
                   inspections: dict[str, list[dict]], phase: str) -> dict:
    relative_leaf = leaf.relative_to(directory).as_posix()
    record = {
        "phase": phase, "geometry": requested["key"], "requested": requested,
        "requested_font_scale": font, "font_percent": round(font * 100),
        "png": f"{relative_leaf}/home.png", "metadata": f"{relative_leaf}/metadata.json",
        "png_exists": (leaf / "home.png").exists(), "png_valid": False,
        "metadata_exists": (leaf / "metadata.json").exists(), "inspection_verified": False,
        "status": "missing", "problems": [], "visual_flags": [], "visual_issues": [], "hidden_views": [],
        "verifier_correction": None,
        "unsafe_views": [], "raw_metadata": {},
    }
    png_path = leaf / "home.png"
    if record["png_exists"]:
        data = png_path.read_bytes()
        if (len(data) >= 33 and data[:8] == b"\x89PNG\r\n\x1a\n"
                and data[12:16] == b"IHDR" and data[-12:-4] == b"\0\0\0\0IEND"):
            width, height = struct.unpack(">II", data[16:24])
            record.update({"png_valid": True, "actual_png_width": width,
                           "actual_png_height": height, "png_sha256": hashlib.sha256(data).hexdigest()})
        else:
            record["problems"].append("PNG отсутствует в полном виде или не прошёл проверку заголовка/конца файла.")
    if record["metadata_exists"]:
        try:
            metadata = read_json(leaf / "metadata.json")
            if not isinstance(metadata, dict):
                raise ValueError("metadata must be an object")
            record["raw_metadata"] = metadata
        except (ValueError, OSError) as error:
            record["problems"].append(f"Метаданные ещё не прочитаны полностью: {error}")
    metadata = record["raw_metadata"]
    density = effective_density(metadata)
    actual_font = metadata.get("font_scale")
    record.update({"actual_density_dpi": density, "actual_font_scale": actual_font,
                   "native_initial_fit": metadata.get("native_initial_fit"),
                   "card_geometry_fit": metadata.get("native_initial_fit"),
                   "native_scrollable": metadata.get("native_scrollable"),
                   "apk_sha256": metadata.get("apk_sha256"),
                   "captured_at": metadata.get("captured_at"), "avd": metadata.get("avd")})
    if record["png_valid"] and density:
        record["actual_width_dp"] = record["actual_png_width"] * 160 / density
        record["actual_height_dp"] = record["actual_png_height"] * 160 / density
    record["requested_orientation"] = orientation(requested.get("width_px"), requested.get("height_px"))
    record["actual_orientation"] = orientation(record.get("actual_png_width"), record.get("actual_png_height"))
    record["orientation_changed"] = (record["png_valid"]
                                     and record["requested_orientation"] != record["actual_orientation"])
    for inspection in reversed(inspections.get(record["png"], [])):
        if (record["png_valid"] and inspection.get("sha256") == record.get("png_sha256")
                and inspection.get("actual_png_width") == record.get("actual_png_width")
                and inspection.get("actual_png_height") == record.get("actual_png_height")):
            record.update({"inspection_verified": True, "inspection": inspection,
                           "inspected_at": inspection.get("inspected_at")})
            break
    if record["png_valid"] and not record["inspection_verified"]:
        record["problems"].append("Нет записи визуального просмотра, совпадающей с SHA-256 и размерами этого PNG.")
    if record["png_valid"] and not metadata:
        record["problems"].append("PNG сохранён, но пригодные метаданные ещё отсутствуют.")
    if metadata and (actual_font is None or not isinstance(actual_font, (float, int))
                     or abs(actual_font - font) > 0.00001):
        record["problems"].append("Фактический масштаб текста не совпадает с запросом или не записан.")
    if metadata and density != requested.get("density_dpi"):
        record["problems"].append("Фактическая плотность не совпадает с запросом или не записана.")
    if record["png_exists"]:
        record["status"] = "pending"
    if (record["png_valid"] and metadata and record["inspection_verified"]
            and not record["problems"] and isinstance(record["native_initial_fit"], bool)):
        record["status"] = "fit" if record["native_initial_fit"] else "needs-designer"
    elif record["png_valid"] and metadata and record["native_initial_fit"] is None:
        record["problems"].append("Классификация начального кадра в метаданных ещё отсутствует.")
    hidden = [TAG_LABELS.get(tag, tag) for tag in metadata.get("missing_initial_tags", [])]
    hidden += [TEXT_LABELS.get(text, text) for text in metadata.get("missing_initial_text", [])]
    record["hidden_views"] = list(dict.fromkeys(hidden))
    record["unsafe_views"] = [TAG_LABELS.get(tag, tag) for tag in metadata.get("unsafe_initial_frames", [])]
    if record["orientation_changed"]:
        record["visual_flags"].append("Запрошенная ориентация отличается от фактического PNG; это не проверка горизонтального Home.")
    observation = record.get("inspection", {}).get("visual_observation", "")
    if (phase == "after" and requested["key"] in REWARDS_NAVIGATION_FINDING_GEOMETRIES
            and record["font_percent"] == 200 and record["inspection_verified"]
            and re.search(r"Rewards", observation, re.I)
            and re.search(r"wrap|clip|перенос|обрез", observation, re.I)):
        record["visual_issues"].append({
            "id": "NAV-REWARDS-200-MDPI", "reason_ru": REWARDS_NAVIGATION_FINDING_REASON,
            "evidence_png": record["png"], "evidence_png_sha256": record["png_sha256"],
            "inspection_utc": record["inspected_at"], "observation_original": observation,
        })
        # Preserve the original native_initial_fit: Home card geometry can
        # pass while the navigation label still fails visual review.
        if record["status"] == "fit":
            record["status"] = "needs-designer"
    if re.search(r"nearly touch|almost touch|adjacent|tight|почти|вплотную", observation, re.I):
        record["visual_flags"].append("Нижние карточки почти соприкасаются. Требуется оценить комфортные интервалы.")
    if re.search(r"unused|phone-sized|phone-width|floor space|свободн|пустот", observation, re.I):
        record["visual_flags"].append("В просмотренном кадре много неиспользованного места; стоит пересмотреть планшетные пропорции.")
    # This one historical diagnostic has an existing verifier audit. Its
    # original metadata remains false, while the verified, corrected result
    # is shown separately and never enters the final-APK case totals.
    audit_path = leaf / "classification-audit.json"
    if (phase == "diagnostic" and requested["key"] == "720x1560-320dpi"
            and record["font_percent"] == 100 and record["inspection_verified"]
            and not record["problems"] and metadata.get("native_initial_fit") is False
            and metadata.get("native_scrollable") is False and not metadata.get("missing_initial_text")
            and re.search(r"visually fits", observation, re.I)
            and re.search(r"clickable-child error", observation, re.I) and audit_path.is_file()):
        audit = read_json(audit_path)
        if audit.get("native_initial_fit") is True and audit.get("outcome") == "initial-frame fit":
            record["verifier_correction"] = {
                "corrected_initial_fit": True, "original_native_initial_fit": False,
                "reason_ru": DIAGNOSTIC_VERIFIER_CORRECTION_REASON,
                "audit_path": audit_path.relative_to(directory).as_posix(),
                "audit_sha256": hashlib.sha256(audit_path.read_bytes()).hexdigest(), "raw_audit": audit,
            }
            record["status"] = "fit"
    summary = metadata.get("required_tag_bounds", {}).get("home_summary", [])
    card = metadata.get("helpful_card_bounds")
    if density and len(summary) == 1 and card:
        record["lower_card_gap_dp"] = (card[1] - summary[0][3]) * 160 / density
    if density and card and metadata.get("controls_top_px") is not None:
        record["navigation_clearance_dp"] = (metadata["controls_top_px"] - card[3]) * 160 / density
    record["overall_visual_fit"] = (record["status"] == "fit" if record["status"] in {"fit", "needs-designer"} else None)
    record["status_label"] = STATUS_LABELS[record["status"]]
    return record


def capture_figure(record: dict, before: dict | None = None, appendix: bool = False) -> str:
    status = record["status"]
    heading = f'{record["font_percent"]}% текста' if not appendix else f'{record["avd"] or record["phase"]} · {record["font_percent"]}%'
    requested = record["requested"]
    if record["png_valid"]:
        preview = (f'<a class="image-link" href="{escape(quote(record["png"], safe="/"))}" target="_blank" rel="noopener">'
                   f'<img src="{escape(quote(record["png"], safe="/"))}" width="{record["actual_png_width"]}" '
                   f'height="{record["actual_png_height"]}" loading="lazy" '
                   f'alt="Home: {escape(record["geometry"])}; фактически {escape(record["actual_orientation"])}; '
                   f'{record["font_percent"]}% текста; {escape(record["status_label"])}"></a>')
    else:
        preview = '<div class="empty-preview"><span>Нет готового PNG</span><small>Этот кадр ещё не является доказательством результата.</small></div>'
    density = record.get("actual_density_dpi")
    facts = [
        ("Запрошено", f'{dimensions(requested.get("width_dp"), requested.get("height_dp"))} dp · {record["requested_orientation"]}'),
        ("Фактически", f'{dimensions(record.get("actual_width_dp"), record.get("actual_height_dp"))} dp · {record["actual_orientation"]}'),
        ("PNG / плотность", f'{dimensions(record.get("actual_png_width"), record.get("actual_png_height"))} px · {density or "—"} dpi'),
        ("Системный текст", f'{number(record.get("actual_font_scale"))}×; запрос {number(record["requested_font_scale"])}×'),
    ]
    if record["verifier_correction"]:
        facts.append(("Исходный verifier", "native_initial_fit=false; ошибка поиска кликабельного дочернего узла"))
        facts.append(("Перепроверка диагностики", "полный кадр помещается; подтверждены audit и просмотр"))
    elif record["card_geometry_fit"] is not None:
        facts.append(("Размещение Home (XML)", "критерий пройден" if record["card_geometry_fit"] else "критерий не пройден"))
    if record["visual_issues"]:
        facts.append(("Визуальный итог", "дефект подписи Rewards; нужен дизайнер"))
    if record.get("navigation_clearance_dp") is not None:
        facts.append(("До навигации", number(record["navigation_clearance_dp"]) + " dp; минимум 8 dp"))
    if record.get("lower_card_gap_dp") is not None:
        facts.append(("Между нижними карточками", number(record["lower_card_gap_dp"]) + " dp"))
    facts_html = "".join(f'<div><dt>{escape(label)}</dt><dd>{escape(value)}</dd></div>' for label, value in facts)
    messages = []
    if record["verifier_correction"]:
        messages.append(f'<p class="notice"><strong>Ошибка старого проверяющего скрипта:</strong> {escape(record["verifier_correction"]["reason_ru"])}</p>')
    if status == "needs-designer":
        if record["card_geometry_fit"] and record["visual_issues"]:
            messages.append('<p class="decision">Карточки Home помещаются, но общий визуальный результат не пройден: дефект в навигации. Передать дизайнеру без уменьшения текста.</p>')
        else:
            messages.append('<p class="decision">Остановить уплотнение: нужна другая композиция. Сохранить читаемый текст и все действия.</p>')
    messages += [f'<p class="notice"><strong>Подтверждённая визуальная проблема:</strong> {escape(issue["reason_ru"])}</p>' for issue in record["visual_issues"]]
    if record["hidden_views"]:
        messages.append(f'<p><strong>Не найдено в первом XML-кадре:</strong> {escape(", ".join(record["hidden_views"]))}.</p>')
    if record["unsafe_views"]:
        messages.append(f'<p><strong>За безопасной границей или у навигации:</strong> {escape(", ".join(record["unsafe_views"]))}.</p>')
    if record["native_scrollable"] is not None:
        messages.append(f'<p class="small">Прокрутка в native XML: {"есть" if record["native_scrollable"] else "нет"}.</p>')
    messages += [f'<p class="notice">{escape(message)}</p>' for message in record["visual_flags"] + record["problems"]]
    if status == "pending" and record["native_initial_fit"] is not None:
        provisional = "помещается" if record["native_initial_fit"] else "не помещается"
        messages.append(f'<p class="small">Предварительный результат размещения по XML: {provisional}. В общий итог проверенных случаев ещё не включён.</p>')
    links = []
    if record["png_valid"]:
        links.append(link(record["png"], "Оригинальный PNG", True))
    if record["metadata_exists"]:
        links.append(link(record["metadata"], "Метаданные"))
    if record["verifier_correction"]:
        links.append(link(record["verifier_correction"]["audit_path"], "Аудит ошибки verifier"))
    xml = str(Path(record["metadata"]).with_name("hierarchy.xml"))
    if record.get("xml_exists"):
        links.append(link(xml, "Native XML"))
    detail = []
    if record.get("png_sha256"):
        detail.append(f'<p>SHA-256 PNG: <code>{escape(record["png_sha256"])}</code></p>')
    if record.get("apk_sha256"):
        detail.append(f'<p>SHA-256 APK: <code>{escape(record["apk_sha256"])}</code></p>')
    if record.get("captured_at"):
        detail.append(f'<p>Снято: {escape(record["captured_at"])}<br>Просмотрено: {escape(record.get("inspected_at") or "нет совпадающей отметки")}</p>')
    observation = record.get("inspection", {}).get("visual_observation")
    if observation:
        detail.append(f'<p><strong>Исходная запись визуального просмотра:</strong> {escape(observation)}</p>')
    if record["raw_metadata"].get("missing_initial_text"):
        detail.append(f'<p>Исходный список отсутствующего текста: {escape(" · ".join(record["raw_metadata"]["missing_initial_text"]))}</p>')
    before_html = ""
    if before is not None:
        before_html = ('<details class="before"><summary>До изменений — исходный кадр</summary>'
                       + capture_figure(before, appendix=True) + '</details>')
    return (f'<article class="capture {status}" data-status="{status}" data-font="{record["font_percent"]}">'
            f'<div class="capture-heading"><h3>{escape(heading)}</h3><span class="pill {status}">{escape(record["status_label"])}</span></div>'
            f'<figure>{preview}</figure><dl>{facts_html}</dl>{"".join(messages)}'
            f'<p class="asset-links">{" · ".join(links)}</p>'
            f'<details class="provenance"><summary>Источник, хеши и наблюдение</summary>{"".join(detail)}</details>{before_html}</article>')


STYLE = """
:root{--paper:#f6f3eb;--ink:#183f30;--body:#303d35;--muted:#687369;--line:#d9dfd4;--green:#dcead9;--amber:#f4e5c8;--pending:#e9e5f0}
*{box-sizing:border-box}body{margin:0;background:var(--paper);color:var(--body);font:16px/1.6 system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}a{color:#24563b;text-underline-offset:3px}a:hover{color:#143c27}main{max-width:1480px;margin:auto;padding:48px 32px 80px}h1,h2,h3,p{margin-top:0}h1{font-size:clamp(30px,4vw,58px);line-height:1.1;letter-spacing:-1.8px;color:var(--ink);max-width:1000px;margin-bottom:22px}h2{font-size:26px;line-height:1.3;color:var(--ink);margin-bottom:12px}h3{font-size:17px;line-height:1.35;margin-bottom:0;color:var(--ink)}.eyebrow{font-size:13px;font-weight:750;letter-spacing:1.3px;text-transform:uppercase}.lead{max-width:1000px;font-size:19px}.small,small{font-size:13px;color:var(--muted)}.updated{font-size:13px;color:var(--muted)}.stats{display:grid;grid-template-columns:repeat(5,1fr);gap:12px;margin:26px 0}.stat{background:#fffdf8;border:1px solid var(--line);border-radius:16px;padding:20px}.stat strong{display:block;font-size:30px;line-height:1.2;color:var(--ink)}.stat span{font-size:14px}.notes{display:grid;grid-template-columns:repeat(3,1fr);gap:18px;margin:26px 0 32px}.notes>div{border-top:3px solid #9db298;padding-top:14px}.notes strong{color:var(--ink)}.notes p{font-size:14px;margin:6px 0}.legend{display:flex;flex-wrap:wrap;gap:8px;margin:14px 0}.pill{display:inline-block;border-radius:999px;padding:5px 10px;font-size:12px;font-weight:650;line-height:1.4;white-space:normal}.pill.fit{background:var(--green);color:#284b2c}.pill.needs-designer{background:var(--amber);color:#70521d}.pill.pending{background:var(--pending);color:#5c4676}.pill.missing{background:#eceee9;color:#647064}.filters{display:grid;grid-template-columns:2fr repeat(3,1fr);gap:12px;background:#fffcf7;border:1px solid var(--line);border-radius:18px;padding:18px;margin:28px 0 10px}.filters label{font-size:13px;font-weight:650;color:var(--ink)}input,select{display:block;width:100%;margin-top:6px;min-height:48px;padding:10px 12px;border:1px solid #bcc9ba;border-radius:10px;background:#fff;color:var(--body);font:inherit;font-size:15px}input:focus,select:focus,summary:focus-visible,a:focus-visible{outline:3px solid #aac477;outline-offset:3px}.group{margin-top:34px;padding-top:28px;border-top:1px solid #cbd5c6;scroll-margin-top:12px}.group-heading{display:flex;justify-content:space-between;gap:20px;align-items:flex-start;margin-bottom:16px}.geometry{font-size:14px;font-family:ui-monospace,SFMono-Regular,monospace;color:var(--muted)}.aliases{max-width:720px;font-size:13px;color:var(--muted);margin:6px 0}.comparison{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px}.comparison.three{grid-template-columns:repeat(3,minmax(0,1fr))}.capture{min-width:0;background:#fffdf8;border:1px solid var(--line);border-radius:18px;padding:18px;align-self:start}.capture-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:10px;margin-bottom:16px}figure{margin:0 0 18px;background:#ebe8df;border:1px solid #e0e1d9;border-radius:12px;overflow:hidden;min-height:240px;display:flex;align-items:center;justify-content:center;padding:14px}.image-link{display:block;text-align:center;line-height:0;width:100%}img{display:block;max-width:100%;width:auto;height:auto;max-height:520px;margin:auto;border-radius:6px;box-shadow:0 3px 15px #17382418}.empty-preview{display:flex;flex-direction:column;gap:12px;text-align:center;justify-content:center;min-height:270px;padding:20px;color:var(--muted)}.empty-preview span{font-size:18px;font-weight:650}dl{margin:0 0 16px}dl>div{display:grid;grid-template-columns:128px 1fr;gap:12px;margin-bottom:6px;font-size:13px}dt{color:var(--muted)}dd{margin:0;overflow-wrap:anywhere}.capture>p{font-size:14px;line-height:1.6;margin-bottom:12px}.decision{padding:12px;background:#fbf1dd;border-radius:10px;color:#755221}.notice{border-left:3px solid #d3b77c;padding-left:12px}.asset-links{font-size:14px}.provenance,.before{border-top:1px solid var(--line);padding-top:10px;margin-top:12px;font-size:13px}summary{cursor:pointer;color:var(--ink);padding:8px 0}code{font:12px/1.7 ui-monospace,SFMono-Regular,monospace;overflow-wrap:anywhere}.provenance p{margin:12px 0}.before .capture{margin-top:12px;background:#f7f7f0}.before img{max-height:320px}.before .capture-heading h3{font-size:13px}.appendix{margin-top:48px;border-top:1px solid var(--line);padding-top:30px}.appendix .comparison{grid-template-columns:repeat(3,minmax(0,1fr))}.table-wrap{overflow:auto;margin-top:18px}table{border-collapse:collapse;width:100%;font-size:13px;background:#fffdf8}th,td{text-align:left;padding:12px;border-bottom:1px solid var(--line);vertical-align:top}th{color:var(--ink);background:#e7edde}td code{font-size:12px}.limit{background:#e8eee3;border-radius:16px;padding:20px;margin-top:26px}.limit p{font-size:14px;margin:8px 0}.resource-links{display:flex;flex-wrap:wrap;gap:8px 20px;font-size:14px}.warning-list{padding:16px 22px;background:#fff1da;border-radius:12px}.noscript{padding:12px;background:#f5e4bf}footer{margin-top:50px;color:var(--muted);font-size:13px}.capture[hidden],.group[hidden]{display:none!important}
@media(max-width:1000px){.comparison.three{grid-template-columns:repeat(2,minmax(0,1fr))}.filters{grid-template-columns:repeat(2,1fr)}.stats{grid-template-columns:repeat(3,1fr)}.notes{grid-template-columns:1fr}.appendix .comparison{grid-template-columns:repeat(2,minmax(0,1fr))}}
@media(max-width:620px){main{padding:28px 16px 50px}.stats{grid-template-columns:repeat(2,1fr)}.stat{padding:16px}.filters,.comparison,.comparison.three,.appendix .comparison{grid-template-columns:1fr}.group-heading{display:block}dl>div{grid-template-columns:110px 1fr}h1{letter-spacing:-1px}.capture{padding:15px}}
@media print{.filters{display:none}main{max-width:none;padding:10px}.group{break-inside:avoid}.capture{break-inside:avoid}img{max-height:320px}.stats{grid-template-columns:repeat(5,1fr)}a{color:inherit}body{background:#fff}}
.appendix .avd-group .comparison{grid-template-columns:repeat(2,minmax(0,1fr))}
@media(max-width:620px){.appendix .avd-group .comparison{grid-template-columns:1fr}}
"""

FILTER_SCRIPT = """
const search = document.getElementById('search');
const status = document.getElementById('status');
const font = document.getElementById('font');
const orientation = document.getElementById('orientation');
function filterGallery() {
  let visible = 0;
  const query = search.value.trim().toLocaleLowerCase('ru');
  document.querySelectorAll('.group').forEach(group => {
    const matchesText = group.dataset.search.toLocaleLowerCase('ru').includes(query);
    const matchesOrientation = orientation.value === 'all' ||
      (orientation.value === 'changed' && group.dataset.orientationChanged === 'true') ||
      (orientation.value === 'unchanged' && group.dataset.orientationChanged === 'false');
    let any = false;
    group.querySelectorAll(':scope > .comparison > .capture').forEach(card => {
      card.hidden = !(matchesText && matchesOrientation &&
        (status.value === 'all' || card.dataset.status === status.value) &&
        (font.value === 'all' || card.dataset.font === font.value));
      any ||= !card.hidden;
      visible += card.hidden ? 0 : 1;
    });
    group.hidden = !any;
  });
  document.getElementById('filter-count').textContent = `Показано случаев: ${visible}`;
}
[search, status, font, orientation].forEach(control => {
  control.addEventListener('input', filterGallery);
  control.addEventListener('change', filterGallery);
});
filterGallery();
"""


def render_html(data: dict) -> str:
    stats, cases, before = data["summary"], data["cases"], data["before"]
    extras, combined = data["additional_summary"], data["combined_summary"]
    historical = data["historical_summary"]
    before_by_key = {(entry["geometry"], entry["font_percent"]): entry for entry in before}
    groups: dict[str, list[dict]] = {}
    for case in cases:
        groups.setdefault(case["geometry"], []).append(case)
    sections = []
    for index, (geometry, rows) in enumerate(groups.items(), 1):
        requested = rows[0]["requested"]
        aliases = requested.get("devices", [])
        rows.sort(key=lambda row: {100: 0, 200: 1, 130: 2}.get(row["font_percent"], 3))
        captures = "".join(capture_figure(row, before_by_key.get((geometry, row["font_percent"]))) for row in rows)
        search_text = geometry + " " + " ".join(aliases) + " " + dimensions(requested["width_dp"], requested["height_dp"])
        sections.append(
            f'<section class="group" id="geometry-{escape(geometry)}" data-search="{escape(search_text)}" '
            f'data-orientation-changed="{str(any(row["orientation_changed"] for row in rows)).lower()}">'
            f'<div class="group-heading"><div><p class="eyebrow">Геометрия {index:02d} / {len(groups)}</p>'
            f'<h2>{escape(dimensions(requested["width_dp"], requested["height_dp"]))} dp по запросу</h2>'
            f'<div class="geometry">{escape(geometry)}</div><p class="aliases">Эквивалентные определения / контрольные размеры: '
            f'{escape(" · ".join(aliases) or "нет обозначений")}</p></div></div>'
            f'<div class="comparison{" three" if len(rows) > 2 else ""}">{captures}</div></section>')
    alias_rows = []
    for index, alias in enumerate(data["sdk_definitions"], 1):
        geometries = [f'{screen["width_px"]}x{screen["height_px"]}-{screen["density_dpi"]}dpi' for screen in alias.get("screens", [])]
        references = [link(f'#geometry-{key}', key) if key in groups else escape(key) for key in geometries]
        alias_rows.append(f'<tr><td>{index}</td><td><code>{escape(alias["id"])}</code></td><td>{escape(alias["name"])}</td>'
                          f'<td>{escape(alias.get("manufacturer", ""))}</td><td>{"<br>".join(references)}</td></tr>')
    avd_rows, avd_sections = [], []
    for avd in data["separate_avds"]:
        captures = sorted([row for row in data["additional_captures"] if row.get("avd") == avd.get("name")],
                          key=lambda row: row["font_percent"])
        verified = sum(row["inspection_verified"] for row in captures)
        evidence = f'{len(captures)} PNG, {verified} совпадающих отметок просмотра' if captures else "Снимков выполнения в папке пока нет"
        ledger_status = ("Испытаны при 100% / 200%; удалены (запись ledger)"
                         if avd.get("status") == "tested100/200 and deleted" else avd.get("status", "статус не записан"))
        avd_rows.append(f'<tr><td><code>{escape(avd.get("name"))}</code></td><td>{escape(avd.get("device_definition"))}</td>'
                        f'<td>{escape(avd.get("expected_geometry"))}</td><td>{escape(ledger_status)}<br>{escape(evidence)}</td></tr>')
        if captures:
            requested = captures[0]["requested"]
            search_text = (f'{avd.get("name")} {avd.get("device_definition")} {avd.get("expected_geometry")} '
                           f'{dimensions(requested["width_dp"], requested["height_dp"])} '
                           f'{" ".join(requested.get("devices", []))}')
            avd_sections.append(
                f'<section class="group avd-group" id="avd-{escape(avd["name"])}" data-search="{escape(search_text)}" '
                f'data-orientation-changed="{str(any(row["orientation_changed"] for row in captures)).lower()}">'
                f'<div class="group-heading"><div><p class="eyebrow">Отдельный AVD · API 35 x86_64</p>'
                f'<h2>{escape(avd.get("device_definition"))}</h2><p class="geometry">{escape(avd.get("name"))} · '
                f'{escape(avd.get("expected_geometry"))}</p></div></div>'
                f'<div class="comparison">{"".join(capture_figure(row) for row in captures)}</div></section>')
    diagnostics = "".join(capture_figure(row, appendix=True) for row in data["diagnostic"])
    appendix = "".join(avd_sections)
    warning_html = ""
    if data["warnings"]:
        warning_html = '<div class="warning-list"><strong>Неполные входные данные:</strong><ul>' + "".join(f'<li>{escape(item)}</li>' for item in data["warnings"]) + '</ul></div>'
    visual_issue_cases = [row for row in cases if row["visual_issues"]]
    visual_issue_html = ""
    if visual_issue_cases:
        references = " · ".join(link(f'#geometry-{row["geometry"]}', row["geometry"] + ' / 200%') for row in visual_issue_cases)
        visual_issue_html = (f'<div class="warning-list"><strong>Навигация требует отдельного решения дизайнера.</strong>'
                             f'<p>В {len(visual_issue_cases)} просмотренных PNG подпись Rewards переносится и обрезается. '
                             f'В {stats["geometry_fit_with_visual_issues"]} из них карточки Home проходят native XML-критерий, '
                             'но общий результат остаётся «Нужен дизайнер». Исходное native_initial_fit сохранено.</p>'
                             f'<p>{references}</p></div>')
    capture_state = ("Все кадры основной матрицы и дополнительных AVD сохранены и подтверждены журналом просмотра."
                     if data["capture_review_complete"] else "Не все объявленные кадры сохранены и подтверждены журналом просмотра.")
    return f'''<!doctype html>
<html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="description" content="Исходные native PNG Home: размеры, системный масштаб текста, пределы компоновки и передача дизайнеру.">
<title>Home без прокрутки — галерея для дизайнера</title><style>{STYLE}</style></head><body><main>
<header><p class="eyebrow">Super Nutrition · разбор Home</p><h1>Home целиком.<br>Читаемость остаётся условием.</h1>
<p class="lead">Сравните исходные кадры при 100%, 200% и выбранных 130% системного текста. Где все блоки не помещаются, уплотнение остановлено: дизайнеру нужен читаемый исходник и точное описание ограничений.</p>
<p class="updated">Срез галереи: {escape(data["generated_at"])}. {escape(capture_state)} Это конечный набор viewport-проверок; общего успешного результата для всех размеров нет.</p></header>
<div class="stats"><div class="stat"><strong>{combined["captured"]} / {combined["planned"]}</strong><span>новых PNG: матрица + AVD</span></div>
<div class="stat"><strong>{combined["fit"]}</strong><span>помещаются; дефекты не отмечены</span></div><div class="stat"><strong>{combined["needs-designer"]}</strong><span>нужно решение дизайнера</span></div>
<div class="stat"><strong>{combined["pending"]}</strong><span>ожидают проверки</span></div><div class="stat"><strong>{combined["missing"]}</strong><span>снимков пока нет</span></div></div>
<p><strong>Основная матрица:</strong> {stats["captured"]}/{stats["planned"]} PNG, {stats["fit"]} fit / {stats["needs-designer"]} дизайнер.
<strong>Три отдельных AVD:</strong> {extras["captured"]}/{extras["planned"]} PNG, {extras["fit"]} fit / {extras["needs-designer"]} дизайнер.
<strong>Общий итог:</strong> {combined["fit"]} / {combined["needs-designer"]} из {combined["inspected"]} подтверждённых просмотров.</p>
<div class="notes"><div><strong>Шрифты сохраняются</strong><p>Токены Fraunces/Nunito и системный масштаб текста сохранены. Нет уменьшения шрифта, обрезки слов или удаления блоков ради зелёного результата; интерактивные рамки сохраняют минимум 48 dp.</p></div>
<div><strong>Первый кадр, без жеста прокрутки</strong><p>«Помещается» требует native XML-результата, записи просмотра именно этого PNG с совпадающими SHA-256/размерами и отсутствия зафиксированных визуальных дефектов. Размещение Home и общий визуальный итог указаны раздельно. Последний блок должен быть выше плавающих контролов минимум на 8 dp.</p></div>
<div><strong>Короткий экран — остановка для дизайнера</strong><p>Читаемый overflow сохраняется. В карточке перечислены отсутствующие в первом XML блоки и пересекающие безопасную границу рамки. Почти соприкасающиеся нижние карточки и свободное место на планшетах отмечены отдельно.</p></div></div>
<div class="legend">{''.join(f'<span class="pill {key}">{escape(label)}</span>' for key, label in STATUS_LABELS.items())}</div>
<p class="small">{stats["geometries"]} геометрий пиксели/плотность, {len(data["sdk_definitions"])} сохранённых определений SDK, {stats["planned"]} запланированных случаев. Один кадр может представлять несколько эквивалентных определений SDK. Это не {stats["geometries"]} аппаратных моделей и не физические устройства.</p>
<nav class="resource-links" aria-label="Исходные материалы">{link('screen-matrix.csv', 'Таблица CSV основной матрицы')} {link('gallery-data.json', 'Полные данные JSON')} {link('gallery-validation.json', 'Проверка галереи')} {link('README.md', 'Описание и повторный запуск')} {link('matrix-plan.json', 'Полный план')} {link('sdk-device-catalog.json', 'Каталог SDK')} {link('visual-inspection.jsonl', 'Журнал просмотра')}</nav>
{warning_html}{visual_issue_html}<div class="filters"><label>Поиск размера / SDK<input id="search" type="search" placeholder="Например: 320, Pixel, Nexus"></label>
<label>Результат<select id="status"><option value="all">Все результаты</option>{''.join(f'<option value="{key}">{escape(label)}</option>' for key, label in STATUS_LABELS.items())}</select></label>
<label>Системный текст<select id="font"><option value="all">100 / 200 / 130%</option><option value="100">100%</option><option value="200">200%</option><option value="130">130%</option></select></label>
<label>Ориентация PNG<select id="orientation"><option value="all">Все случаи</option><option value="changed">Отличается от запроса</option><option value="unchanged">Не отличается / ещё нет PNG</option></select></label></div>
<p id="filter-count" class="small" aria-live="polite">Показано случаев: {combined["captured"]}</p><noscript><p class="noscript">JavaScript отключён: ниже доступна вся галерея, исходные файлы и статусы.</p></noscript>
{''.join(sections)}
<section class="appendix"><h2>Все определения SDK сохранены</h2><p>Здесь перечислены все {len(data["sdk_definitions"])} определения исходного каталога, включая общие и именованные пресеты. Совпадение размеров означает общий viewport-кадр, а не отдельный запуск каждой модели. Для складных устройств показан базовый дисплей; закрытый корпус, сгиб, другие позы и физические сенсоры не проверены.</p>
<details><summary>Показать {len(data["sdk_definitions"])} определений и ссылки на геометрию</summary><div class="table-wrap"><table><thead><tr><th>№</th><th>ID SDK</th><th>Имя</th><th>Производитель</th><th>Размер / плотность</th></tr></thead><tbody>{''.join(alias_rows)}</tbody></table></div></details></section>
<section class="appendix"><h2>Три отдельных AVD — пары 100% / 200%</h2><p>Дополнительная серия: {extras["captured"]}/{extras["planned"]} PNG, {extras["inspected"]} подтверждённых просмотров; {extras["fit"]} fit и {extras["needs-designer"]} для дизайнера.
Эти пресеты дополняют основную матрицу API 35 x86_64. Испытания и удаление {extras["deleted_presets"]} временных AVD отражены в {link('separate-avds/ledger.json', 'ledger.json')}; конфигурации и изображения сохранены. Это не физические устройства и не проверка исходной ОС старого Nexus.</p>
<div class="table-wrap"><table><thead><tr><th>AVD</th><th>Определение</th><th>Ожидаемая геометрия</th><th>Сохранённое состояние / доказательство</th></tr></thead><tbody>{''.join(avd_rows)}</tbody></table></div>{appendix}</section>
<section class="appendix"><h2>Исторические кадры и промежуточная диагностика</h2><p>{len(data["diagnostic"])} диагностический PNG не входит в матрицу после изменений. Его APK и стадия отличаются; использовать только вместе с его собственными метаданными. Исходных кадров до изменений: {len(before)}, они доступны внутри соответствующих сравнений выше.</p>
<p>Подтверждённых просмотров исторических кадров: {historical["inspected"]}/{historical["captured"]}; ожидают проверки: {historical["pending"]}. Пять кадров до изменений показывают исходные ограничения. Диагностический кадр визуально помещается: ложный отрицательный результат старого verifier объяснён ниже со ссылкой на сохранённый аудит. Эти исторические кадры другого APK не входят в {combined["inspected"]} финальных просмотров и общий итог {combined["fit"]}/{combined["needs-designer"]}.</p><div class="comparison">{diagnostics}</div></section>
<div class="limit"><strong>Границы доказательств</strong><p>Основная серия — native-кадры одного API 35 x86_64 эмулятора с изменением viewport и плотности. Android-приложение закреплено в портретной ориентации; фактические размеры PNG показаны независимо от запроса. Системные панели и taskbar учитываются отдельно. Серия не доказывает совместимость всех аппаратных моделей, закрытые позы foldable или работу на физических устройствах.</p>
<p>Проверенный UI-набор: 167 тестов, 0 неуспешных тестов. Это не зелёный полный quality gate: корневая политика имеет истёкшее bootstrap/native-доказательство, а полная компиляция SharedKit ограничена отсутствием требуемого Xcode. Галерея не утверждает завершение всех проверок, коммит, push или merge.</p>
<p>Превью меняет только размер отображения в браузере. PNG-файлы не обрезаны, не пересохранены и не масштабированы генератором; ссылка открывает исходный полный кадр. Запись просмотра требуется и для последнего PNG: сохранённый, но ещё не подтверждённый кадр остаётся «Ожидает проверки».</p></div>
<footer>Повторный запуск из корня проекта: <code>python3 scripts/generate-home-design-gallery.py</code>. Статусы относятся к сохранённому срезу, а не к будущим кадрам.</footer>
</main><script>{FILTER_SCRIPT}</script></body></html>'''


def render_csv(cases: list[dict], before: list[dict]) -> str:
    before_by_key = {(row["geometry"], row["font_percent"]): row for row in before}
    fields = ["case", "geometry", "sdk_aliases", "requested_px", "requested_dp", "requested_density_dpi",
              "requested_orientation", "requested_font_scale", "actual_png_px", "actual_dp",
              "actual_density_dpi", "actual_orientation", "actual_font_scale", "status", "status_ru",
              "native_initial_fit", "card_geometry_fit", "overall_visual_fit", "visual_issue_ids", "visual_issues_ru",
              "native_scrollable", "visual_inspection_verified", "hidden_initial_views",
              "unsafe_initial_views", "navigation_clearance_dp", "lower_card_gap_dp", "observations_ru",
              "visual_observation_original", "capture_utc", "inspection_utc", "avd", "apk_sha256",
              "png_sha256", "after_png", "before_png", "metadata"]
    buffer = io.StringIO(newline="")
    writer = csv.DictWriter(buffer, fieldnames=fields)
    writer.writeheader()
    for index, row in enumerate(cases, 1):
        requested = row["requested"]
        baseline = before_by_key.get((row["geometry"], row["font_percent"]))
        writer.writerow({
            "case": index, "geometry": row["geometry"], "sdk_aliases": " | ".join(requested.get("devices", [])),
            "requested_px": dimensions(requested["width_px"], requested["height_px"]),
            "requested_dp": dimensions(requested["width_dp"], requested["height_dp"]),
            "requested_density_dpi": requested["density_dpi"], "requested_orientation": row["requested_orientation"],
            "requested_font_scale": row["requested_font_scale"],
            "actual_png_px": dimensions(row.get("actual_png_width"), row.get("actual_png_height")),
            "actual_dp": dimensions(row.get("actual_width_dp"), row.get("actual_height_dp")),
            "actual_density_dpi": row.get("actual_density_dpi"), "actual_orientation": row["actual_orientation"],
            "actual_font_scale": row["actual_font_scale"], "status": row["status"], "status_ru": row["status_label"],
            "native_initial_fit": row["native_initial_fit"], "native_scrollable": row["native_scrollable"],
            "card_geometry_fit": row["card_geometry_fit"], "overall_visual_fit": row["overall_visual_fit"],
            "visual_issue_ids": " | ".join(issue["id"] for issue in row["visual_issues"]),
            "visual_issues_ru": " | ".join(issue["reason_ru"] for issue in row["visual_issues"]),
            "visual_inspection_verified": row["inspection_verified"],
            "hidden_initial_views": " | ".join(row["hidden_views"]), "unsafe_initial_views": " | ".join(row["unsafe_views"]),
            "navigation_clearance_dp": row.get("navigation_clearance_dp"), "lower_card_gap_dp": row.get("lower_card_gap_dp"),
            "observations_ru": " | ".join(row["visual_flags"] + row["problems"]),
            "visual_observation_original": row.get("inspection", {}).get("visual_observation", ""),
            "capture_utc": row.get("captured_at"), "inspection_utc": row.get("inspected_at"), "avd": row.get("avd"),
            "apk_sha256": row.get("apk_sha256"), "png_sha256": row.get("png_sha256"),
            "after_png": row["png"] if row["png_valid"] else "",
            "before_png": baseline["png"] if baseline and baseline["png_valid"] else "",
            "metadata": row["metadata"] if row["metadata_exists"] else "",
        })
    return buffer.getvalue()


def render_readme(data: dict) -> str:
    stats = data["summary"]
    extras, combined = data["additional_summary"], data["combined_summary"]
    historical = data["historical_summary"]
    visual_cases = [row for row in data["cases"] if row["visual_issues"]]
    visual_details = "\n".join(f'- [{row["geometry"]} / 200%]({row["png"]}): размещение Home по XML — {"пройдено" if row["card_geometry_fit"] else "не пройдено"}; общий итог — нужен дизайнер.' for row in visual_cases)
    capture_state = ("Основная конечная матрица и дополнительная серия AVD полностью сохранены и подтверждены журналом просмотра."
                     if data["capture_review_complete"] else "Часть объявленных кадров ещё не сохранена или не подтверждена журналом просмотра.")
    return f'''# Home без прокрутки — передача дизайнеру

Откройте [галерею](index.html): размеры сгруппированы, рядом показаны 100% и 200% текста, для четырёх границ — ещё 130%. Там, где сохранён исходник, внутри карточки доступно сравнение «до изменений». Оригинальные PNG открываются по ссылкам в полном разрешении.

{capture_state} Срез на {data["generated_at"]}; это итог сохранённых кадров, не успешный результат для всех размеров и не объявление завершения всех quality/device-проверок.

| Серия | PNG / объявлено | Подтверждённые просмотры | Fit без отмеченных дефектов | Нужен дизайнер |
| --- | ---: | ---: | ---: | ---: |
| Основная матрица | {stats["captured"]}/{stats["planned"]} | {stats["inspected"]} | {stats["fit"]} | {stats["needs-designer"]} |
| Три отдельных AVD | {extras["captured"]}/{extras["planned"]} | {extras["inspected"]} | {extras["fit"]} | {extras["needs-designer"]} |
| **Общий итог** | **{combined["captured"]}/{combined["planned"]}** | **{combined["inspected"]}** | **{combined["fit"]}** | **{combined["needs-designer"]}** |

В новых сериях ожидающих просмотра: **{combined["pending"]}**, отсутствующих снимков: **{combined["missing"]}**. Пять PNG до изменений и один диагностический исходник доступны отдельно: **{historical["inspected"]}/{historical["captured"]}** подтверждённых просмотров, **{historical["pending"]}** pending. Они относятся к предшествующим APK и не входят в общий итог 100 финальных случаев.

- «Помещается»: `native_initial_fit=true`, готовый PNG, совпадающие запрос/фактический масштаб и плотность, запись визуального просмотра с совпадающими SHA-256/размерами PNG и отсутствие зафиксированного визуального дефекта.
- «Нужен дизайнер»: те же доказательства, но `native_initial_fit=false` **или есть подтверждённый визуальный дефект**. Исходное `native_initial_fit` не изменяется; `card_geometry_fit` отдельно повторяет этот XML-результат, `overall_visual_fit` отражает общий итог. Читаемый overflow сохраняется. Не уменьшаем шрифты, не скрываем блоки и не прокручиваем экран, чтобы объявить первый кадр успешным.
- «Ожидает проверки»: PNG уже есть, но не хватает пригодных метаданных или записи просмотра именно этого файла, либо обнаружено несоответствие данных. Предварительный результат XML не превращается в проверенный fit.
- «Снимка пока нет»: нет готового кадра запланированного случая. Поле запроса не выдаётся за фактические размеры.

## Историческая диагностика: ошибка verifier, а не отрицательный результат экрана

Пять исходников «до» повторно просмотрены и подтверждены журналом; они показывают реальные исходные no-fit ограничения. [Диагностический кадр 360×780 / 100%](diagnostic/720x1560-320dpi/font-100/home.png) также повторно просмотрен и визуально показывает полный Home и Do it выше навигации.

В его неизменяемом `metadata.json` осталось `native_initial_fit=false`. Причина — ошибка старого capture-verifier: он искал кликабельный description-узел Do it, хотя подпись является некликабельным ребёнком внутри кликабельного родителя с той же рамкой 48 dp. Сохранённый [classification-audit.json](diagnostic/720x1560-320dpi/font-100/classification-audit.json) подтверждает родительскую рамку и зазор карточки 23 px (11,5 dp) до контролов. Свежая запись просмотра сверяет SHA-256 и размеры именно этого исходного PNG.

Галерея показывает исправленный **диагностический** результат отдельно в `verifier_correction`; исходные `native_initial_fit=false`, `card_geometry_fit=false` и raw metadata не меняются. Это промежуточный APK, поэтому исправленный результат диагностики не добавляет fit к финальным 100 случаям. Нельзя читать старый false как новую продуктовую ошибку или считать диагностический APK финальным доказательством.

Сохранены существующие токены Fraunces/Nunito, системный масштаб текста, полные подписи и минимальные 48 dp интерактивные рамки. Дизайнеру нужно решить композицию коротких экранов и масштабирование текста через компоновку; отдельно оценить почти соприкасающиеся нижние карточки и неиспользованное место на планшетах. Карточки показывают, какие блоки отсутствуют в начальном XML или пересекают безопасную границу, и дают исходную запись наблюдения.

## Размещение карточек и визуальные дефекты — разные результаты

Native XML-критерий размещения Home проходят **{stats["geometry_fit"]}** случаев. В **{stats["visual_issue_cases"]}** просмотренных кадрах при 200% подпись вкладки Rewards переносится и обрезается. В **{stats["geometry_fit_with_visual_issues"]}** из них карточки Home помещаются, но навигация сохраняет визуальный дефект: поэтому общий положительный итог только **{stats["fit"]}**, а не {stats["geometry_fit"]}.

{visual_details}

Эти детерминированные записи привязаны к указанным исходным PNG и совпадающим записям журнала, содержащим наблюдение о Rewards. На непроверенные дополнительные AVD дефект не экстраполируется. В CSV/JSON раздельно сохранены исходный `native_initial_fit`, результат размещения карточек, общий итог и причина визуального дефекта.

## Объём и границы

План содержит {stats["geometries"]} точных геометрий пиксели/плотность: по 100% и 200% плюс четыре случая 130%, всего {stats["planned"]}. Все {len(data["sdk_definitions"])} определения SDK/их ID и имена сохранены в раскрываемом индексе галереи и JSON. Эквивалентные определения делят один viewport-кадр; это не отдельные запуски {stats["geometries"]} аппаратных моделей.

Основная серия — native API 35 x86_64 эмулятор с viewport overrides. Фактические размеры взяты из PNG IHDR, плотность и `font_scale` — из записанных runtime-метаданных. Запрошенные и фактические dp и ориентации представлены раздельно. Home закреплён портретно: горизонтальный запрос, отрисованный портретно, не доказывает landscape-интерфейс. Системные insets/taskbar и область окна могут дополнительно уменьшать полезную площадь.

Три отдельные AVD дополняют основную серию: у каждого есть пара исходных PNG 100%/200% и подтверждения просмотра. В [ledger](separate-avds/ledger.json) отражено удаление **{extras["deleted_presets"]}** временных AVD после испытаний; их конфигурации и доказательства сохранены. Это API 35 x86_64, а не исходная ОС/физическая совместимость старых Nexus. Закрытые позы foldable, сгибы, другие display posture и физические устройства не проверены этой галереей. Контракт портретной ориентации не изменялся.

Проверенный UI-набор — 167 тестов, 0 неуспешных тестов. Полный quality gate не объявлен зелёным: корневая политика ограничена истёкшим bootstrap/native-доказательством, полная компиляция SharedKit — отсутствием требуемого Xcode. Этот пакет не утверждает завершение всех проверок, коммит, push или merge.

## Файлы и повторный запуск

- [index.html](index.html) — русская галерея и фильтры; работает локально, без внешних ресурсов.
- [screen-matrix.csv](screen-matrix.csv) — ровно {stats["planned"]} рядов основной матрицы, ссылки до/после, requested/actual поля, хеши и статусы. UTF-8 с BOM для корректного открытия русских подписей.
- [gallery-data.json](gallery-data.json) — полный срез данных и отдельные/общие итоги; все определения SDK, шесть дополнительных AVD-кадров, до/после/диагностика и предупреждения.
- [gallery-validation.json](gallery-validation.json) — производная проверка инвентаря, ссылок, PNG-хешей и неизменённых значений native metadata; не новое device-доказательство.
- [matrix-plan.json](matrix-plan.json), [sdk-device-catalog.json](sdk-device-catalog.json), [visual-inspection.jsonl](visual-inspection.jsonl) и файлы каждого кадра — неизменяемые входные доказательства. Диагностический APK не входит в основную после-матрицу.

Из корня репозитория:

```sh
python3 scripts/generate-home-design-gallery.py
```

Нужна только стандартная библиотека Python 3. Генератор записывает только `index.html`, `README.md`, `screen-matrix.csv`, `gallery-data.json` и `gallery-validation.json`; PNG, метаданные, план, журнал просмотра и capture-скрипты не меняет. Не запускает ADB, эмуляторы, Gradle, Git или сетевые команды. Повторный запуск воспроизводит галерею из сохранённого пакета; любой позднее добавленный PNG без подтверждения в журнале останется `pending`.

Превью масштабируется только CSS в браузере. Исходное изображение не пересохраняется, не обрезается и не изменяется. Хеш PNG сверяется с журналом, чтобы поздняя перезапись не получила статус старой проверки.
'''


def summarize_records(records: list[dict], planned: int) -> dict:
    statuses = Counter(row["status"] for row in records)
    statuses["missing"] += max(0, planned - len(records))
    summary = {key: statuses[key] for key in STATUS_LABELS}
    summary.update({"planned": planned, "geometries": len({row["geometry"] for row in records}),
                    "captured": sum(row["png_valid"] for row in records),
                    "inspected": sum(row["inspection_verified"] for row in records),
                    "geometry_fit": sum(row["card_geometry_fit"] is True and row["status"] in {"fit", "needs-designer"} for row in records),
                    "visual_issue_cases": sum(bool(row["visual_issues"]) for row in records),
                    "geometry_fit_with_visual_issues": sum(row["card_geometry_fit"] is True and bool(row["visual_issues"]) for row in records)})
    return summary


def build_snapshot(directory: Path) -> dict:
    plan = read_json(directory / "matrix-plan.json")
    catalog = read_json(directory / "sdk-device-catalog.json")
    if not isinstance(plan, dict) or not plan.get("cases") or not isinstance(catalog, dict) or not catalog.get("devices"):
        raise ValueError("A nonempty plan and SDK catalog are required; existing gallery is preserved")
    warnings: list[str] = []
    inspections = load_inspections(directory, warnings)
    cases = []
    seen = set()
    for item in plan["cases"]:
        key, font = item["geometry"], float(item["font_scale"])
        if (key, font) in seen:
            raise ValueError(f"Duplicate planned case: {key}, {font}")
        seen.add((key, font))
        requested = dict(catalog.get("unique_geometries", {}).get(key) or geometry_from_key(key))
        requested["key"] = key
        leaf = directory / "after" / key / f"font-{round(font * 100)}"
        row = capture_record(directory, leaf, requested, font, inspections, "after")
        row["xml_exists"] = (leaf / "hierarchy.xml").exists()
        cases.append(row)
    before, diagnostic, additional = [], [], []
    known = {row["metadata"] for row in cases}
    for metadata_path in sorted(directory.rglob("metadata.json")):
        relative = metadata_path.relative_to(directory).as_posix()
        if relative in known or not metadata_path.with_name("home.png").exists():
            continue
        try:
            metadata = read_json(metadata_path)
            requested = dict(metadata["requested_geometry"])
            key = f'{requested["width_px"]}x{requested["height_px"]}-{requested["density_dpi"]}dpi'
            requested["key"] = key
            phase = metadata_path.relative_to(directory).parts[0]
            row = capture_record(directory, metadata_path.parent, requested, float(metadata["font_scale"]), inspections, phase)
            row["xml_exists"] = metadata_path.with_name("hierarchy.xml").exists()
            (before if phase == "before" else diagnostic if phase == "diagnostic" else additional).append(row)
        except (ValueError, KeyError, TypeError, OSError) as error:
            warnings.append(f"Дополнительный кадр {relative} ещё не прочитан: {error}")
    ledger_path = directory / "separate-avds/ledger.json"
    ledger = read_json(ledger_path) if ledger_path.exists() else []
    summary = summarize_records(cases, len(cases))
    extra_summary = summarize_records(additional, len(ledger) * 2)
    extra_summary.update({"presets": len(ledger), "deleted_presets": sum(bool(entry.get("deleted_at")) for entry in ledger)})
    combined_summary = summarize_records(cases + additional, len(cases) + extra_summary["planned"])
    return {"generated_at": dt.datetime.now(dt.timezone.utc).isoformat(), "summary": summary,
            "additional_summary": extra_summary, "combined_summary": combined_summary,
            "historical_summary": summarize_records(before + diagnostic, len(before + diagnostic)),
            "capture_review_complete": (combined_summary["captured"] == combined_summary["planned"]
                                        and combined_summary["inspected"] == combined_summary["planned"]
                                        and combined_summary["pending"] == combined_summary["missing"] == 0),
            "cases": cases, "before": before, "diagnostic": diagnostic, "additional_captures": additional,
            "sdk_definitions": catalog["devices"], "separate_avds": ledger, "warnings": warnings,
            "evidence_scope": "API35 x86_64 viewport overrides; actual AVD evidence listed separately; no physical-device or closed-fold-posture claim"}


class GalleryLinkAudit(HTMLParser):
    """Read-only audit of generated local links and original image references."""

    def __init__(self) -> None:
        super().__init__()
        self.ids: set[str] = set()
        self.links: list[str] = []
        self.images: list[str] = []

    def handle_starttag(self, tag: str, attributes: list[tuple]) -> None:
        attrs = dict(attributes)
        if "id" in attrs:
            if attrs["id"] in self.ids:
                raise ValueError(f"Duplicate gallery anchor: {attrs['id']}")
            self.ids.add(attrs["id"])
        if tag == "a" and "href" in attrs:
            self.links.append(unquote(attrs["href"]))
        if tag == "img":
            self.images.append(unquote(attrs["src"]))


def validate_gallery(directory: Path, data: dict, outputs: dict[str, str]) -> dict:
    """Validate derived inventory; this is not new device or visual evidence."""
    audit = GalleryLinkAudit()
    audit.feed(outputs["index.html"])
    for reference in audit.links:
        if reference.startswith("#"):
            if reference[1:] not in audit.ids:
                raise ValueError(f"Missing gallery anchor: {reference}")
        elif reference not in outputs and reference != "gallery-validation.json" and not (directory / reference).is_file():
            raise ValueError(f"Missing local gallery link: {reference}")
    records = data["cases"] + data["additional_captures"] + data["before"] + data["diagnostic"]
    valid_images = [row for row in records if row["png_valid"]]
    if Counter(audit.images) != Counter(row["png"] for row in valid_images):
        raise ValueError("A saved original PNG is omitted or referenced more than once")
    for row in valid_images:
        if hashlib.sha256((directory / row["png"]).read_bytes()).hexdigest() != row["png_sha256"]:
            raise ValueError(f"PNG changed during gallery generation: {row['png']}")
        if read_json(directory / row["metadata"]) != row["raw_metadata"]:
            raise ValueError(f"Metadata changed during gallery generation: {row['metadata']}")
        if row["native_initial_fit"] != row["raw_metadata"].get("native_initial_fit"):
            raise ValueError(f"Original native classification changed: {row['png']}")
        if row["verifier_correction"]:
            correction = row["verifier_correction"]
            if read_json(directory / correction["audit_path"]) != correction["raw_audit"]:
                raise ValueError("Diagnostic audit changed during gallery generation")
            if hashlib.sha256((directory / correction["audit_path"]).read_bytes()).hexdigest() != correction["audit_sha256"]:
                raise ValueError("Diagnostic audit digest no longer matches")
    pairs = {}
    for avd in data["separate_avds"]:
        rows = [row for row in data["additional_captures"] if row.get("avd") == avd["name"]]
        fonts = sorted(row["font_percent"] for row in rows)
        if len(fonts) != len(set(fonts)):
            raise ValueError(f"Duplicate font capture for separate AVD: {avd['name']}")
        pairs[avd["name"]] = {"font_percents": fonts, "paired_100_200": fonts == [100, 200],
                              "inspected": sum(row["inspection_verified"] for row in rows),
                              "ledger_status": avd.get("status"), "deleted_at": avd.get("deleted_at")}
    csv_rows = list(csv.DictReader(io.StringIO(outputs["screen-matrix.csv"])))
    if len(csv_rows) != len(data["cases"]):
        raise ValueError("Main CSV row count does not match the finite plan")
    if data["sdk_definitions"] != read_json(directory / "sdk-device-catalog.json")["devices"]:
        raise ValueError("SDK definitions are not fully preserved")
    historical = data["before"] + data["diagnostic"]
    corrections = [{"png": row["png"], "original_native_initial_fit": row["native_initial_fit"],
                    "corrected_initial_fit": row["verifier_correction"]["corrected_initial_fit"],
                    "audit_path": row["verifier_correction"]["audit_path"]}
                   for row in historical if row["verifier_correction"]]
    return {
        "kind": "derived gallery consistency validation; no new device execution or visual inspection",
        "validated_at": dt.datetime.now(dt.timezone.utc).isoformat(),
        "main_summary": data["summary"], "additional_summary": data["additional_summary"],
        "combined_summary": data["combined_summary"], "csv_main_rows": len(csv_rows),
        "sdk_definitions_preserved": len(data["sdk_definitions"]), "local_links_checked": len(audit.links),
        "original_png_links_checked": len(audit.images), "original_png_hashes_checked": len(valid_images),
        "original_native_metadata_preserved": len(valid_images), "avd_pairs": pairs,
        "historical_pngs": len(historical), "historical_journal_verified": sum(row["inspection_verified"] for row in historical),
        "historical_summary": data["historical_summary"], "diagnostic_verifier_corrections": corrections,
        "generator_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        "input_sha256": {name: hashlib.sha256((directory / name).read_bytes()).hexdigest()
                         for name in ["matrix-plan.json", "sdk-device-catalog.json", "visual-inspection.jsonl", "separate-avds/ledger.json"]
                         + [item["audit_path"] for item in corrections]
                         if (directory / name).is_file()},
        "output_sha256": {name: hashlib.sha256(content.encode("utf-8-sig" if name.endswith(".csv") else "utf-8")).hexdigest()
                          for name, content in outputs.items()},
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path, default=DEFAULT_DIRECTORY, help="Existing evidence folder; output is generated alongside its immutable inputs")
    directory = parser.parse_args().directory.resolve()
    data = build_snapshot(directory)
    # Prepare and validate output in memory before replacing derived files.
    outputs = {
        "index.html": render_html(data), "README.md": render_readme(data),
        "screen-matrix.csv": render_csv(data["cases"], data["before"]),
        "gallery-data.json": json.dumps(data, indent=2, ensure_ascii=False) + "\n",
    }
    outputs["gallery-validation.json"] = json.dumps(validate_gallery(directory, data, outputs), indent=2, ensure_ascii=False) + "\n"
    for name, content in outputs.items():
        (directory / name).write_text(content, encoding="utf-8-sig" if name.endswith(".csv") else "utf-8")
    print(json.dumps({"gallery": str(directory / "index.html"), **data["summary"],
                      "additional": data["additional_summary"], "combined": data["combined_summary"],
                      "sdk_definitions": len(data["sdk_definitions"]), "warnings": data["warnings"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
