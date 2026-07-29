#!/usr/bin/env node
import { readFileSync, writeFileSync, readdirSync, existsSync, mkdirSync } from 'fs';
import { resolve, join } from 'path';

const API_KEY = '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';
const BASE = 'https://openrouter.ai/api/v1';
const IMG_DIR = resolve(join(import.meta.dirname, 'images'));
const OUT_DIR = resolve(join(import.meta.dirname, 'results'));

const MODELS = [
  { id: 'openai/gpt-4o-mini',       label: 'GPT-4o-mini (Pass 1 fallback)', pass: 1 },
  { id: 'google/gemini-2.5-flash',  label: 'Gemini 2.5 Flash (Pass 1 main)', pass: 1 },
  { id: 'openai/gpt-4o',            label: 'GPT-4o (Pass 2)', pass: 2 },
  { id: 'anthropic/claude-sonnet-5', label: 'Claude Sonnet 5 (Pass 2 alt)', pass: 2 },
];

const PROMPT = `Identify the food in this photo. Return ONLY valid JSON with no markdown or extra text:
{
  "foods": [
    {
      "label": "string (food name in English)",
      "rank": 1,
      "confidence": 0.0-1.0,
      "assumptions": ["string (any visual assumptions made)"]
    }
  ]
}`;

function costEstimate(model, usage) {
  const rates = {
    'openai/gpt-4o-mini':        { in: 0.15, out: 0.60 },
    'google/gemini-2.5-flash':   { in: 0.15, out: 0.60 },
    'openai/gpt-4o':             { in: 2.50, out: 10.00 },
    'anthropic/claude-sonnet-5': { in: 2.00, out: 10.00 },
  };
  const r = rates[model] || { in: 1, out: 2 };
  const inTokens = usage?.prompt_tokens || 0;
  const outTokens = usage?.completion_tokens || 0;
  return (inTokens / 1e6 * r.in) + (outTokens / 1e6 * r.out);
}

function parseJSON(text) {
  const cleaned = text.replace(/```json\s*/gi, '').replace(/```\s*/g, '').trim();
  try { return JSON.parse(cleaned); } catch { return null; }
}

async function runInference(modelId, imagePath) {
  const image = readFileSync(imagePath);
  const b64 = image.toString('base64');
  const mime = imagePath.endsWith('.png') ? 'image/png' : 'image/jpeg';

  const start = performance.now();
  const resp = await fetch(`${BASE}/chat/completions`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${API_KEY}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      model: modelId,
      messages: [
        { role: 'user', content: [
          { type: 'text', text: PROMPT },
          { type: 'image_url', image_url: { url: `data:${mime};base64,${b64}` } },
        ]},
      ],
      max_tokens: 512,
      temperature: 0,
    }),
  });
  const duration = performance.now() - start;

  if (!resp.ok) {
    const err = await resp.text();
    return { model: modelId, error: err, duration };
  }

  const json = await resp.json();
  const msg = json.choices?.[0]?.message?.content || '';
  const usage = json.usage || {};
  const parsed = parseJSON(msg);
  const cost = costEstimate(modelId, usage);

  return {
    model: modelId,
    modelVersion: json.model || modelId,
    provider: json.provider || 'unknown',
    duration,
    durationSec: (duration / 1000).toFixed(2),
    promptTokens: usage.prompt_tokens || 0,
    completionTokens: usage.completion_tokens || 0,
    totalTokens: (usage.prompt_tokens || 0) + (usage.completion_tokens || 0),
    cost,
    raw: msg.slice(0, 200),
    parsed,
    foods: parsed?.foods || [],
  };
}

function resultKey(modelId, fileName) {
  return `${modelId.replace(/[/\s]/g, '_')}__${fileName.replace(/[.\s]/g, '_')}`;
}

async function main() {
  if (!existsSync(IMG_DIR)) {
    console.error(`ERROR: Put test images in ${IMG_DIR}`);
    process.exit(1);
  }

  const images = readdirSync(IMG_DIR).filter(f => /\.(jpg|jpeg|png)$/i.test(f)).sort();
  if (!images.length) {
    console.error(`ERROR: No images found in ${IMG_DIR}. Add .jpg/.png files.`);
    process.exit(1);
  }

  if (!existsSync(OUT_DIR)) mkdirSync(OUT_DIR, { recursive: true });

  console.log(`Found ${images.length} test images\n`);

  const allResults = [];

  for (const model of MODELS) {
    console.log(`── ${model.label} (${model.id}) ──`);

    for (const img of images) {
      const imgPath = join(IMG_DIR, img);
      const key = resultKey(model.id, img);
      process.stdout.write(`  ${img}: `);

      try {
        const result = await runInference(model.id, imgPath);
        result.image = img;
        result.modelLabel = model.label;
        result.pass = model.pass;
        result.key = key;
        allResults.push(result);

        writeFileSync(join(OUT_DIR, `${key}.json`), JSON.stringify(result, null, 2));

        if (result.error) {
          console.log(`ERROR: ${result.error.slice(0, 100)}`);
        } else if (result.foods.length) {
          console.log(`✓ ${result.foods.map(f => `${f.label} (${(f.confidence * 100).toFixed(0)}%)`).join(', ')} | ${result.durationSec}s | ${result.totalTokens}t | $${result.cost.toFixed(5)}`);
        } else {
          console.log(`✗ no foods parsed | ${result.durationSec}s | raw: ${result.raw.slice(0, 80)}`);
        }
      } catch (e) {
        console.log(`ERROR: ${e.message}`);
        allResults.push({ model: model.id, image: img, key, error: e.message });
      }
    }
  }

  // Generate report
  writeReport(allResults, images, MODELS);
  console.log(`\nReport: ${OUT_DIR}/BENCHMARK-REPORT.md`);
}

function writeReport(allResults, images, models) {
  let md = `# SuperNutrition Model Benchmark Report\n\n`;
  md += `- **Date:** ${new Date().toISOString().split('T')[0]}\n`;
  md += `- **Models tested:** ${models.map(m => m.label).join(', ')}\n`;
  md += `- **Test images:** ${images.join(', ')}\n\n`;

  md += `## Results by Model\n\n`;

  for (const model of models) {
    const results = allResults.filter(r => r.model === model.id);
    const success = results.filter(r => r.foods?.length);
    const errors = results.filter(r => r.error);

    md += `### ${model.label}\n\n`;
    md += `| Metric | Value |\n|--------|-------|\n`;
    md += `| Success rate | ${success.length}/${results.length}\n`;
    if (errors.length) md += `| Errors | ${errors.map(e => e.image).join(', ')}\n`;

    if (success.length) {
      const avgDur = success.reduce((s, r) => s + r.duration, 0) / success.length;
      const avgCost = success.reduce((s, r) => s + r.cost, 0) / success.length;
      const avgTokens = success.reduce((s, r) => s + r.totalTokens, 0) / success.length;
      const avgConf = success.reduce((s, r) => {
        const c = r.foods.filter(f => f.confidence).reduce((a, f) => a + f.confidence, 0);
        return s + (r.foods.filter(f => f.confidence).length ? c / r.foods.filter(f => f.confidence).length : 0);
      }, 0) / success.length;
      const totalCost = success.reduce((s, r) => s + r.cost, 0);

      md += `| Avg latency | ${(avgDur / 1000).toFixed(2)}s\n`;
      md += `| Avg cost/req | $${avgCost.toFixed(5)}\n`;
      md += `| Total cost | $${totalCost.toFixed(5)}\n`;
      md += `| Avg tokens | ${avgTokens.toFixed(0)}\n`;
      md += `| Avg confidence | ${(avgConf * 100).toFixed(1)}%\n`;
    }

    md += `\n| Image | Foods Found | Confidence | Latency | Tokens | Cost |\n|-------|-------------|-----------|---------|--------|------|\n`;
    for (const r of results) {
      if (r.error) {
        md += `| ${r.image} | ERROR | — | ${(r.duration / 1000).toFixed(2)}s | — | — |\n`;
      } else if (r.foods.length) {
        const foods = r.foods.map(f => `${f.label} (#${f.rank})`).join(', ');
        const confs = r.foods.map(f => f.confidence ? `${(f.confidence * 100).toFixed(0)}%` : '—').join(', ');
        md += `| ${r.image} | ${foods} | ${confs} | ${r.durationSec}s | ${r.totalTokens} | $${r.cost.toFixed(5)} |\n`;
      } else {
        md += `| ${r.image} | ✗ no parse | — | ${r.durationSec}s | ${r.totalTokens} | $${r.cost.toFixed(5)} |\n`;
      }
    }
    md += `\n`;
  }

  md += `## Summary\n\n`;
  md += `| Model | Success | Avg Latency | Avg Cost | Avg Confidence |\n|-------|---------|-----------|---------|----------------|\n`;
  for (const model of models) {
    const results = allResults.filter(r => r.model === model.id);
    const success = results.filter(r => r.foods?.length);
    if (success.length) {
      const avgDur = success.reduce((s, r) => s + r.duration, 0) / success.length / 1000;
      const avgCost = success.reduce((s, r) => s + r.cost, 0) / success.length;
      const avgConf = success.reduce((s, r) => {
        const c = r.foods.filter(f => f.confidence).reduce((a, f) => a + f.confidence, 0);
        return s + (r.foods.filter(f => f.confidence).length ? c / r.foods.filter(f => f.confidence).length : 0);
      }, 0) / success.length;
      md += `| ${model.label} | ${success.length}/${results.length} | ${avgDur.toFixed(2)}s | $${avgCost.toFixed(5)} | ${(avgConf * 100).toFixed(1)}% |\n`;
    } else {
      md += `| ${model.label} | 0/${results.length} | — | — | — |\n`;
    }
  }

  md += `\n## Model Recommendations\n\n`;
  md += `Based on this benchmark:\n\n`;
  const pass1results = allResults.filter(r => r.pass === 1 && r.foods?.length);
  const pass1best = pass1results.length ? pass1results.reduce((b, r) => r.cost < b.cost ? r : b) : null;
  md += `- **Pass 1 (fast):** `;
  md += pass1best ? `Best cost-performance: ${pass1best.modelLabel} ($${pass1best.cost.toFixed(5)}/req, ${pass1best.durationSec}s)` : 'N/A';
  md += `\n`;
  md += `- **Pass 2 (deep):** For low-confidence fallback, use the more capable model\n`;
  md += `- **Confidence threshold ${(0.75 * 100).toFixed(0)}%** — will trigger Pass 2 when primary model is uncertain\n\n`;

  md += `## Raw Results\n\n`;
  md += `Per-request JSON results saved in \`results/\` directory.\n`;

  writeFileSync(join(OUT_DIR, 'BENCHMARK-REPORT.md'), md);
  console.log(`\nReport generated: ${OUT_DIR}/BENCHMARK-REPORT.md`);
}

main().catch(console.error);
