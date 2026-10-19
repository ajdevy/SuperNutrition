#!/usr/bin/env python3
"""Build a focused Home handoff using the existing capture interpretation."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import re
import shlex
from collections import Counter
from datetime import datetime, timezone
from html import escape
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DIRECTORY = ROOT / '_bmad-output/design-review/home-popular-screens-2026-10-18'
spec = importlib.util.spec_from_file_location('home_gallery', ROOT / 'scripts/generate-home-design-gallery.py')
gallery = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gallery)


def build(directory: Path = DEFAULT_DIRECTORY):
    DIRECTORY = directory.resolve()
    selection = gallery.read_json(DIRECTORY / 'selection.json')
    selected_profiles = selection['profiles']
    if len(selected_profiles) != 9:
        raise ValueError('The focused review requires exactly nine profiles (9).')
    if (len({p['id'] for p in selected_profiles}) != 9
            or len({p['geometry'] for p in selected_profiles}) != 9):
        raise ValueError('The focused review requires distinct profile IDs and unique geometries.')
    if Counter(p['kind'] for p in selected_profiles) != {'phone': 6, 'tablet': 3}:
        raise ValueError('The focused review requires six phone and three tablet profiles.')
    if selection['font_scales'] != [1.0, 2.0]:
        raise ValueError('The focused review requires ordered 100% and 200% text scales.')
    expected_digest = selection.get('expected_apk_sha256')
    if expected_digest is not None and (not isinstance(expected_digest, str)
                                        or re.fullmatch(r'[0-9a-f]{64}', expected_digest) is None):
        raise ValueError('Expected production APK digest must be a SHA-256 hex value.')
    title = selection.get('review_title_ru', 'Home на популярных экранах')
    review_note = selection.get('review_note_ru', '')
    build_note = selection.get('build_note_ru', 'Использована та же production-сборка, что и в полной галерее.')
    output_path = DIRECTORY.relative_to(ROOT) if DIRECTORY.is_relative_to(ROOT) else DIRECTORY
    output_argument = '' if DIRECTORY == DEFAULT_DIRECTORY else ' --output ' + shlex.quote(str(output_path))
    update_command = 'python3 scripts/generate-popular-home-design-review.py' + output_argument
    before_png = selection.get('before_png')
    before_link = gallery.link(before_png, 'До правки — 360 × 780', True) if before_png else ''
    catalog = gallery.read_json(DIRECTORY / 'sdk-device-catalog.json')['unique_geometries']
    warnings = []
    inspections = gallery.load_inspections(DIRECTORY, warnings)
    profiles, cases, sections, rows = [], [], [], []
    for profile in selection['profiles']:
        profile = dict(profile)
        records = []
        key = profile['geometry']
        for scale in selection['font_scales']:
            leaf = DIRECTORY / 'after' / key / f'font-{round(scale * 100)}'
            record = gallery.capture_record(DIRECTORY, leaf, catalog[key], scale, inspections, 'after')
            records.append(record)
            cases.append(record)
        profile['captures'] = records
        profiles.append(profile)
        statuses = ''.join(f'<td>{escape(r["status_label"])}</td>' for r in records)
        rows.append(f'<tr><th scope="row"><a href="#{profile["id"]}">{escape(profile["label"])}</a></th>'
                    f'<td>{profile["share_percent"]}%</td>{statuses}</tr>')
        figures = ''.join(gallery.capture_figure(r) for r in records)
        kind = 'Телефоны' if profile['kind'] == 'phone' else 'Планшеты'
        sections.append(f'<section class="group" id="{profile["id"]}" data-kind="{profile["kind"]}" '
                        f'data-search="{escape(profile["label"].lower())}">'
                        f'<div class="group-heading"><div><p class="eyebrow">{kind}</p>'
                        f'<h2>{escape(profile["label"])} dp</h2>'
                        f'<p class="aliases">{profile["share_percent"]}% в категории StatCounter · сентябрь 2026</p>'
                        '</div></div><div class="comparison">'+figures+'</div></section>')
    counts = Counter(c['status'] for c in cases)
    complete = all(c['status'] in {'fit', 'needs-designer'} for c in cases)
    digests = {c['apk_sha256'] for c in cases if c.get('apk_sha256') is not None}
    if (any(not isinstance(digest, str) or re.fullmatch(r'[0-9a-f]{64}', digest) is None for digest in digests)
            or len(digests) > 1
            or (complete and any(c.get('apk_sha256') is None for c in cases))):
        raise ValueError('All verified cases must use one valid production APK digest.')
    if expected_digest is not None and any(digest != expected_digest for digest in digests):
        raise ValueError('Captured APK digest differs from the expected production build.')
    stamp = datetime.now(timezone.utc).isoformat()
    data = {'generated_at': stamp, 'selection': selection, 'profiles': profiles,
            'cases': cases, 'summary': dict(counts), 'complete': complete, 'warnings': warnings,
            'apk_sha256': next(iter(digests), None), 'expected_apk_sha256': expected_digest}
    (DIRECTORY / 'gallery-data.json').write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n')
    source_links = (
        '<a href="https://gs.statcounter.com/screen-resolution-stats/mobile/worldwide/#monthly-202609-202609-bar" '
        'target="_blank" rel="noopener">StatCounter — телефоны</a> · '
        '<a href="https://gs.statcounter.com/screen-resolution-stats/tablet/worldwide/#monthly-202609-202609-bar" '
        'target="_blank" rel="noopener">StatCounter — планшеты</a>'
    )
    summary = f'{len(profiles)} размеров · {len(cases)} вариантов · {counts["fit"]} помещаются · {counts["needs-designer"]} требуют дизайнера'
    progress = '' if complete else f'<p class="notice">Снимки дополняются. Проверено {sum(c["inspection_verified"] for c in cases)} из {len(cases)}. Пустые карточки не являются результатами проверки.</p>'
    script = """
const search = document.getElementById('search');
const status = document.getElementById('status');
const font = document.getElementById('font');
const kind = document.getElementById('kind');
function filterGallery() {
  const query = search.value.trim().toLocaleLowerCase('ru');
  let visible = 0;
  document.querySelectorAll('.group').forEach(group => {
    const matches = group.dataset.search.includes(query) && (kind.value === 'all' || group.dataset.kind === kind.value);
    let any = false;
    group.querySelectorAll('.capture').forEach(card => {
      card.hidden = !(matches && (status.value === 'all' || card.dataset.status === status.value) && (font.value === 'all' || card.dataset.font === font.value));
      any ||= !card.hidden;
      visible += !card.hidden;
    });
    group.hidden = !any;
  });
  document.getElementById('filter-count').textContent = `Показано случаев: ${visible}`;
}
[search, status, font, kind].forEach(control => {
  control.addEventListener('input', filterGallery);
  control.addEventListener('change', filterGallery);
});
filterGallery();
"""
    html = f'''<!doctype html><html lang="ru"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1"><title>{escape(title)}</title>
<style>{gallery.STYLE if hasattr(gallery, 'STYLE') else gallery.CSS}</style></head><body><main>
<header><p class="eyebrow">Super Nutrition · обзор для дизайнера</p>
<h1>{escape(title)}</h1><p class="lead">{summary}.</p>
{f'<p>{escape(review_note)}</p>' if review_note else ''}
<p>Отбор по мировой статистике за сентябрь 2026: шесть ведущих телефонных размеров и три распространённых планшетных в портретной ориентации.</p>
<p>Откройте обычный и увеличенный текст рядом. Там, где весь Home не помещается, сохранён читаемый текст и прокрутка; дальнейшее уменьшение остановлено.</p>
<p class="resource-links"><a href="DESIGNER-NOTES.md">Документ для дизайнера</a><a href="selection.json">Размеры и источники</a>{before_link}</p>
{progress}</header>
<div class="table-wrap"><table><thead><tr><th>Размер</th><th>Доля категории</th><th>100% текста</th><th>200% текста</th></tr></thead><tbody>{''.join(rows)}</tbody></table></div>
<section class="filters" aria-label="Фильтры снимков">
<label>Размер<input id="search" type="search" placeholder="Например, 390 или 820" autocomplete="off"></label>
<label>Устройство<select id="kind"><option value="all">Все</option><option value="phone">Телефоны</option><option value="tablet">Планшеты</option></select></label>
<label>Результат<select id="status"><option value="all">Все</option><option value="fit">Помещается</option><option value="needs-designer">Нужен дизайнер</option><option value="missing">Нет снимка</option><option value="pending">Не просмотрен</option></select></label>
<label>Системный текст<select id="font"><option value="all">100% и 200%</option><option value="100">100%</option><option value="200">200%</option></select></label></section>
<p id="filter-count" aria-live="polite"></p><noscript>Все снимки доступны ниже; фильтры требуют JavaScript.</noscript>
{''.join(sections)}
<section class="methodology"><h2>Что нужно решить в дизайне</h2><p>В вариантах «Нужен дизайнер» нижние метрики или полезный шаг уходят за начальный экран либо пересекаются с плавающей навигацией. Нужна другая композиция с доступными действиями и читаемым текстом. На широких экранах стоит отдельно оценить свободное место и пропорции.</p>
<h2>Как выбраны размеры</h2><p>{source_links}. Доли относятся к веб-наблюдениям внутри каждой категории, а не к установленным экземплярам приложения. Горизонтальный планшетный размер 1280 × 800 не дублируется: Home закреплён в портрете.</p>
<p>Здесь показан настоящий Android Home на API 35 x86_64 при указанных логических размерах dp. Это проверка компоновки на эмуляторе; название размера не означает проверку iPhone или конкретной модели. PNG сохранены без обрезки, пересохранения и изменения пикселей; нажатие открывает оригинал.</p></section>
<footer>Сформировано {datetime.now().strftime('%d.%m.%Y')}. Исходная полная галерея сохранена отдельно. Обновление: <code>{escape(update_command)}</code>.</footer>
</main><script>{script}</script></body></html>'''
    (DIRECTORY / 'index.html').write_text(html)
    table = ['| Размер | Доля категории | 100% | 200% |', '| --- | ---: | --- | --- |']
    for p in profiles:
        values = [f'[{r["status_label"]}]({r["png"]})' for r in p['captures']]
        table.append(f'| {p["label"]} dp | {p["share_percent"]}% | '+ ' | '.join(values) +' |')
    notes = f'''# {title}

Для дизайнера Super Nutrition. В подборке только девять популярных портретных размеров и 18 вариантов обычного и увеличенного текста. {counts['fit']} вариантов помещаются без прокрутки; {counts['needs-designer']} требуют изменения композиции.

{review_note}

{f'До изменения: [исходный кадр 360 × 780]({before_png}). Он относится к предыдущей сборке и не входит в 18 результатов текущей проверки.' if before_png else ''}

Откройте [HTML галерею](index.html), чтобы сравнить полный кадр при 100% и 200% системного текста. Шрифты приложения не уменьшались ради размещения. Для каждого кадра сохранены оригинальный PNG, метаданные и границы элементов.

## Результаты

{chr(10).join(table)}

## Что требуется от дизайна

В отмеченных вариантах весь Home не помещается на первом экране: нижняя часть метрик или полезный шаг оказываются под навигацией либо ниже видимой области. Нужна компактная композиция, которая сохраняет смысл, читаемость и доступные действия. Уменьшение размера текста остановлено. Отдельно стоит оценить свободное место на планшетах.

## Основание выбора

Источник — StatCounter Global Stats, весь мир, сентябрь 2026, последний полный месяц на дату подготовки. Выбраны шесть ведущих телефонных размеров и три распространённых разных портретных размера планшетов. Доли телефонов и планшетов относятся к разным категориям и не суммируются. Размер 1280 × 800 не дублирует планшетную композицию 800 × 1280, поскольку Home закреплён в портретной ориентации.

- [Телефонные размеры](https://gs.statcounter.com/screen-resolution-stats/mobile/worldwide/#monthly-202609-202609-bar)
- [Планшетные размеры](https://gs.statcounter.com/screen-resolution-stats/tablet/worldwide/#monthly-202609-202609-bar)

Веб-статистика задаёт приоритет размеров. Снимки показывают Android API 35 x86_64 с соответствующими логическими размерами в dp; это не проверка iOS или конкретных моделей устройств. {build_note} Старые и редкие размеры в эту подборку не входят.
'''
    (DIRECTORY / 'DESIGNER-NOTES.md').write_text(notes)
    preserve = gallery.read_json(DIRECTORY / 'original-review-preservation.json')
    old = ROOT / preserve['source']
    assert all(hashlib.sha256((old / p).read_bytes()).hexdigest() == digest for p, digest in preserve['files'].items()), 'Original review changed'
    extra_preserved = {}
    for manifest in DIRECTORY.glob('*preservation.json'):
        if manifest.name == 'original-review-preservation.json':
            continue
        snapshot = gallery.read_json(manifest)
        source = ROOT / snapshot['source']
        assert all(hashlib.sha256((source / p).read_bytes()).hexdigest() == digest for p, digest in snapshot['files'].items()), f'Preserved review changed: {source}'
        extra_preserved[snapshot['source']] = snapshot['count']
    print(json.dumps({'directory':str(DIRECTORY),'profiles':len(profiles),'cases':len(cases),'summary':dict(counts),'complete':complete,'warnings':warnings,'original_files_unchanged':preserve['count'],'extra_preserved':extra_preserved},ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=DEFAULT_DIRECTORY)
    build(parser.parse_args().output)
