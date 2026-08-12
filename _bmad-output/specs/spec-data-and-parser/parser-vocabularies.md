# Parser vocabularies for ru / es / en

Companion to `sources-and-parsing-research.md`. That file decided *where* the
linguistics live (build time, table-driven, pure-Kotlin runtime). This file is
the content of those tables: the exact word lists the segmenter needs, what each
one is sourced from, and — the half that actually matters — the words that look
like separators and must never be treated as one.

**This is a specification, not an implementation.** Nothing here was compiled or
run. Where I describe current engine behaviour I read the source; those claims
are marked `[REPO]` and carry a file reference, and the ones that depend on
Kotlin's Unicode predicates are marked as inference rather than measurement.

## Sourcing legend

Every list entry below carries one of these. The distinction is the point of the
document: a list that mixes citable data with a native speaker's intuition and
does not say which is which cannot be reviewed.

| Mark | Meaning |
| --- | --- |
| `[CLDR]` | Read out of CLDR 48 (`cldr-json` 48.2.0) on 2026-08-02. Licence `Unicode-3.0`. Shippable. |
| `[SW-ISO]` | Read out of stopwords-iso on 2026-08-02. MIT (`Copyright (c) 2016 Gene Diaz`). Shippable. |
| `[OFF]` | Read out of the Open Food Facts `taxonomies/food/ingredients.txt`. **ODbL — corroboration only, do not copy into a shipped table.** See the licence note below. |
| `[REPO]` | Read out of this repository's Kotlin sources. |
| `[KNOWLEDGE]` | My own knowledge of the language. **No citable source. Needs a native-speaker pass before it ships.** |

### Retrieval provenance

| Source | URL | Version / date | Licence |
| --- | --- | --- | --- |
| CLDR units + list patterns | `https://raw.githubusercontent.com/unicode-org/cldr-json/main/cldr-json/cldr-units-full/main/{ru,es,en}/units.json`, `.../cldr-misc-full/main/{ru,es,en}/listPatterns.json` | `cldr-core` 48.2.0, `cldrVersion` 48, Unicode 16.0.0. Retrieved 2026-08-02 | Unicode-3.0 |
| stopwords-iso | `https://raw.githubusercontent.com/stopwords-iso/stopwords-{ru,es,en}/master/stopwords-{ru,es,en}.txt` | `master`, retrieved 2026-08-02. ru 559 entries, es 732, en 1298 | MIT |
| Open Food Facts ingredients taxonomy | `https://raw.githubusercontent.com/openfoodfacts/openfoodfacts-server/main/taxonomies/food/ingredients.txt` | `main`, retrieved 2026-08-02, 2,646,611 bytes | ODbL |

**Licence hygiene.** The prior research flagged ODbL share-alike as a lawyer
question. It does not become one here: every word I take from OFF is also
present in stopwords-iso (MIT), and I verified each one in both. OFF is used
below only to corroborate the `con` finding that motivated this document. No
generated table should read from OFF.

---

## 0. What the engine does today

Facts, so the deltas below are deltas against something real.

`mobile/domain/nutrition/src/commonMain/kotlin/com/supernutrition/domain/nutrition/TextFragments.kt`

- `[REPO]` Separators are `,`, a newline anywhere in a whitespace run, and the
  whole-word set `CONJUNCTIONS = { and, with, plus, & }` matched between
  whitespace runs (`TextFragments.separatorAt`, line 56; the set is at line 153).
- `[REPO]` `QUANTITY_WORDS` (line 176) is 48 English entries: articles and `of`,
  `one`–`ten`, `half`, `quarter`, portion nouns, and units.
- `[REPO]` `stripQuantityWords` (line 193) trims **edges only** and returns the
  original query untouched when nothing is trimmed or when every token is a
  quantity. Interior quantity words survive deliberately — the comment names
  `half and half`.
- `[REPO]` `isQuantityToken` (line 214) accepts an all-digit token and a
  digits-then-known-unit token (`200g`, `2tbsp`), consulting `QUANTITY_WORDS`
  rather than a separate unit list.

`.../CatalogSearch.kt`

- `[REPO]` `tokenizeNormalized` (line 346) splits on anything failing
  `Char.isLetterOrDigit()`, deliberately avoiding `Regex` for cross-platform
  determinism.
- `[REPO]` `normalizeForSearch` (line 331) lowercases, expands `ß æ œ`, and folds
  a fixed table of 39 Latin accented characters. **Cyrillic passes through
  untouched**, and there is no Latin↔Cyrillic homoglyph handling.
- `[REPO]` `search` (line 91) already implements *whole query first, relaxed
  query second*: exact → quantity-stripped → preparation-relaxed. This ordering
  is the precedent for the weak-separator rule in §2.4.

`.../SeedDishes.kt` — `[REPO]` the seed dish set already carries
`курица с рисом`, `pollo con arroz` and `huevo con pan` as alternate names.
**Promoting `с` or `con` to a separator makes those three unreachable**, and
`яйцо на ржаном хлебе` on the same dish falls the same way to `на`. The trap is
not hypothetical and does not need the licensed package to arrive to bite.

### Why the three measured failures fail

- `курица и рис` — `и` is not in `CONJUNCTIONS`, `,` is absent, no newline. One
  fragment. `[REPO]`
- `arroz y frijoles negros` — same, `y` is not in `CONJUNCTIONS`. `[REPO]`
- `2x eggs` — `tokenizeNormalized` keeps `2x` as one token (both characters are
  letter-or-digit). `isQuantityToken` takes the digit prefix `2`, leaves `x`,
  and `x` is not in `QUANTITY_WORDS`, so the token is not a quantity and the
  fragment queries for a record containing `2x`. `[REPO]`

Two related cases I did **not** measure and that need a test rather than my
word (Kotlin `Char.isDigit()` is `Nd` only, `isLetterOrDigit()` excludes `Sm`
and `No`):

- `×2 eggs` (U+00D7) probably already works *by accident* — `×` is dropped by
  the tokenizer, leaving the bare `2`, which is stripped.
- `½ avocado` (U+00BD, category `No`) probably works by accident too, because
  the fraction glyph is dropped entirely rather than understood. That is a
  silent quantity loss, not a success: a half avocado and a whole avocado
  produce the same query.

---

## 1. Separators

A separator joins two foods. Below, **strong** means "split on sight";
**weak** means "only split if the whole fragment failed to resolve" (§2.4).

### 1.1 Language-neutral

```
,   ;   \n   +   &   •   ·
```

`[KNOWLEDGE]` for `+ & • ·`; `,` and `\n` are `[REPO]` (already implemented) and
`,` is corroborated as the list-separator in all three locales by CLDR
`listPattern-type-standard`, whose `start`/`middle` pattern is `{0}, {1}` for
`ru`, `es` and `en` alike `[CLDR]`.

**`/` is not a separator.** `1/2 aguacate` is a fraction. If a future revision
wants `/` as a separator it must be excluded when flanked by digits, and the
regression test is `1/2 aguacate y 2 huevos`. `[KNOWLEDGE]`

**`-` is not a separator**, in any of the three: `пол-литра`, `иван-чай`,
`мясо по-французски`, `medialuna`, `half-and-half`. `[KNOWLEDGE]`

### 1.2 English — strong

```
and, with, plus, &
```

`[REPO]` — this is the shipped set; `and` is corroborated by CLDR `en`
`listPattern-type-standard` (`{0}, and {1}` / `{0} and {1}`) `[CLDR]`, and all
four are in stopwords-iso `en` except `&` `[SW-ISO]`.

Candidates to add, all `[KNOWLEDGE]`:

```
as well as, along with, together with, then, followed by, alongside
```

**`with` is already the English `con`.** The shipped set treats `with` as a
strong separator, which is exactly the promotion this document argues against
for Spanish `con` and Russian `с`. It breaks the same class of names —
`macaroni with cheese`, `biscuits with gravy`, `chili with beans`, `chicken with
rice`, `beans with pork` are all real dish names, several of them in the FNDDS
register the dish layer is going to be built from. English `with` should be
**demoted to weak** at the same time Spanish and Russian gain their weak
prepositions, or the three languages will not behave alike. `[KNOWLEDGE]`

### 1.3 Russian — strong

```
и
```

`[CLDR]` — `ru` `listPattern-type-standard` is `{0} и {1}`. Also in
stopwords-iso `ru` `[SW-ISO]`.

I could not find a Russian food or dish name containing a standalone `и` token,
so `и` is safe as a strong separator. That is a negative result from my own
search, not a citable one. `[KNOWLEDGE]`

### 1.4 Russian — weak or excluded

```
плюс, а также, и ещё, и еще
```

`[KNOWLEDGE]`. `плюс` is safe; the multiword forms need phrase matching.

**`да` should not ship as a separator, in either strength.** Its coordinating
sense (`щи да каша`) is folksy and archaic, and its dominant modern sense is
*yes*. A user answering a prompt with `да` and then typing a food, or typing
`да, курица`, would be segmented on an affirmation. It also appears inside the
set phrase `Иван да Марья`. If it ships at all it should ship weak, behind the
whole-fragment-first rule, and with the test case in §6. `[KNOWLEDGE]`

**`с` and `со` are never separators.** See §2.

### 1.5 Spanish — strong

```
y
```

`[CLDR]` — `es` `listPattern-type-standard` is `{0} y {1}`. Also in
stopwords-iso `es` `[SW-ISO]` and in the OFF `stopwords:es:` line `[OFF]`.

`y` is strong **but not unconditional**: it has its own do-not-split list
(§2.2), which `con` made everyone forget it needs.

### 1.6 Spanish — weak or conditional

```
e, más
```

`e` `[SW-ISO]`, `[OFF]`. It is the RAE allomorph of `y` before a word beginning
with `i-` or `hi-` (`peras e higos`), which is `[KNOWLEDGE]` — CLDR models only
the canonical `y` and has no representation of the allomorph. Two reasons to
ship it conditionally rather than as a plain strong separator: it is only valid
in that phonological environment, and it is a single letter, which makes it
cheap to hit by typo. Recommendation: treat `e` as a separator **only** when the
following token starts with `i` or `hi`. `más` is `[KNOWLEDGE]`.

**`con` is never a separator.** See §2.

### 1.7 Transliterated Russian — deliberately no separator

The seed catalog carries Latin-script entries for Cyrillic records (`kuritsa`,
`ris`, `fasol`) `[REPO]`, so Latin-script Russian input is a supported path. It
must **not** gain a transliterated conjunction.

- Latin `i` for `и` collides head-on with the English pronoun **I**, which opens
  the single most common meal-logging sentence there is: `I had two eggs and
  toast` would segment after the first word. `[KNOWLEDGE]`
- Latin `s` for `с` is obviously worse.

Transliterated Russian therefore segments on punctuation, newlines and the
English conjunctions only. That is a stated limitation with a test case (§6),
not an oversight.

---

## 2. Words that must never split — the important half

### 2.1 Spanish `con`

`con` is in the OFF Spanish stopword list `[OFF]` (verified verbatim, line 133
of `taxonomies/food/ingredients.txt`: `stopwords:es: contiene, de, del, la, el,
las, los, con, y, e, …`) and in stopwords-iso `es` `[SW-ISO]`. Promoting it to a
separator — the obvious fix for `arroz y frijoles negros`, since it sits right
next to `y` on both lists — destroys the following.

Dish names that break, `[KNOWLEDGE]` throughout (this is a cuisine list, not a
corpus extract; several are also FNDDS entries but I did not verify them
against a release):

```
arroz con leche
arroz con pollo
arroz con gandules
arroz con habichuelas
arroz con costra
pollo con arroz
café con leche
leche con chocolate
pan con tomate
pan con chicharrón
churros con chocolate
huevos con jamón
huevos con chorizo
huevos con papas
papas con huevo
migas con huevo
frijoles con puerco
chile con carne
chili con carne
carne con papas
callos con garbanzos
lentejas con chorizo
fabada con almejas
bacalao con tomate
garbanzos con espinacas
tostada con aguacate
yogur con granola
avena con leche
gachas con leche
helado con fresas
galletas con crema
```

`pollo con arroz` is already an alternate name on `dish:chicken-and-rice` in
this repository `[REPO]`.

### 2.2 Spanish `y` — the exception list people forget

`y` is a real separator, but it is inside genuine single-dish names too:

```
moros y cristianos
arroz y habichuelas
huevos rancheros y frijoles   (the second half is a side; splitting is right here)
sal y pimienta
aceite y vinagre
pan y aceite
```

`moros y cristianos` is the canonical case: it is one Cuban dish (black beans
and rice), not two foods. `[KNOWLEDGE]`

There is a second, structural failure mode for `y` that no word list catches:
splitting inside a `de`-phrase.

```
sándwich de jamón y queso     → must stay one item, not [sándwich de jamón][queso]
ensalada de atún y maíz
tarta de manzana y canela
```

`[KNOWLEDGE]`. The rule is that a `y` whose left context is inside a `de …`
complement belongs to the complement. The cheap approximation, which I
recommend over trying to parse it: whole-fragment-first (§2.4) resolves all
three of these, because the whole string is a catalog name and the split halves
are not.

### 2.3 Spanish structural prepositions — never separators

```
de, del, a, al, a la, en, sin, para, por, tipo, estilo
```

`[SW-ISO]` for `de del la el las los con y e en por` (all present); `sin` also
`[SW-ISO]`; the rest `[KNOWLEDGE]`.

- `de` — `pechuga de pollo` (in the seed catalog `[REPO]`), `diente de ajo`,
  `cuarto de libra`.
- `a la` / `al` — `pollo a la plancha`, `arroz a la cubana`, `pollo al horno`,
  `merluza a la romana`.
- `en` — `atún en aceite`, `atún en agua` (these are different nutritionally;
  splitting or dropping `en aceite` understates fat).
- **`sin` is a raised-severity case.** `yogur sin azúcar`, `café sin azúcar`,
  `leche sin lactosa`. Splitting or dropping the negation does not merely lose
  the match — it *adds a food the user explicitly excluded*. A separator error
  that moves the nutrition number up is worse than one that returns nothing.

### 2.4 Russian `с` / `со`

Russian `с` and `со` are the instrumental preposition and carry exactly the risk
`con` does. Both are in stopwords-iso `ru` `[SW-ISO]`. Open Food Facts cannot
even be consulted here: **`ingredients.txt` declares `stopwords:` for 28
languages and Russian is not one of them** `[OFF]` — a measured gap, consistent
with the prior research's finding of only 974 Russian OFF entries.

Dish names that break, `[KNOWLEDGE]` throughout:

```
кофе с молоком
чай с лимоном
чай с мёдом
каша с молоком
овсянка с ягодами
рис с овощами
курица с рисом
гречка с грибами
макароны с сыром
картофель с укропом
пельмени со сметаной
вареники с вишней
блины с творогом
блины с икрой
блины со сгущёнкой
творог со сметаной
сырники со сметаной
бутерброд с сыром
бутерброд с колбасой
хлеб с маслом
пирожки с капустой
пирожки с мясом
голубцы с мясом
плов с бараниной
йогурт с гранолой
салат с тунцом
суп с фрикадельками
```

`курица с рисом` is already an alternate name on `dish:chicken-and-rice`
`[REPO]`.

`со` is the pre-consonant-cluster variant (`со сметаной`, `со сгущёнкой`) and
must be listed alongside `с`, not derived. `[KNOWLEDGE]`

### 2.5 Russian structural prepositions — never separators

```
с, со, без, в, во, из, изо, на, под, по, от, для, к, ко, у, при, за
```

All `[SW-ISO]`. The food-specific reasons are `[KNOWLEDGE]`:

- `под` — `сельдь под шубой`, `мясо под сыром`.
- `на` — `яйцо на ржаном хлебе` (already a seed alternate name `[REPO]`),
  `каша на молоке` vs `каша на воде` — a nutritionally load-bearing distinction.
- `из` — `суп из шампиньонов`, `котлеты из индейки`.
- `по-` — `мясо по-французски`, `курица по-грузински` (hyphenated, so the
  hyphen rule in §1.1 already covers it).
- **`без` is the raised-severity case**, exactly like Spanish `sin`: `кофе без
  сахара`, `йогурт без сахара`, `хлеб без глютена`. Dropping it inverts the
  meaning and inflates the numbers.

### 2.6 The mechanism, not just the list

Two mechanisms, and they should both exist because they fail differently.

**(a) Weak separators are deferred.** Never split on a weak separator on the
first pass. Resolve the whole fragment; only if it produces no hit at any tier,
re-attempt with weak separators enabled. This is the same shape as the existing
`CatalogSearch.search` cascade — exact, then quantity-stripped, then
preparation-relaxed `[REPO]` — so it is a pattern already in the codebase rather
than a new concept.

It handles the general case with no vocabulary at all: `arroz con leche`
resolves whole, so it is never split; `курица с рисом и салат` resolves the
`курица с рисом` half whole and splits the `и`.

**(b) A protected-phrase index still earns its keep**, because (a) only protects
names the catalog already knows. Before the licensed package arrives the catalog
is eight foods and four dishes `[REPO]`, so (a) alone would split
`café con leche` — nothing in the catalog matches it — and hand back coffee plus
a full serving of milk. The protected list above is what covers the gap, and it
should be matched longest-first so `arroz con leche` wins over `arroz`.

**(c) The negation words are neither.** `sin`, `без` and English `without` are
not weak separators that might split — they must **never** split and must never
be dropped as stopwords either, because both failures change the answer in the
same direction. They belong in their own class with their own test.

---

## 3. Quantity vocabulary

Order of application is §5; this section is only the content.

### 3.1 Language-neutral patterns

Patterns, not words, so they do not become an endless list:

| Pattern | Examples | Notes |
| --- | --- | --- |
| Integer | `2`, `200`, `1000` | `[REPO]` already handled |
| Decimal | `1.5`, `1,5` | The comma decimal is standard in ru and es. **This collides with `,` as a separator** — `1,5 л молока` must not split. `[KNOWLEDGE]` |
| ASCII fraction | `1/2`, `3/4`, `1 1/2` | `/` must not be a separator (§1.1) |
| Vulgar fraction | `½ ⅓ ⅔ ¼ ¾ ⅕ ⅛` (U+00BD, U+2153, U+2154, U+00BC, U+00BE, U+2155, U+215B) | Currently dropped by the tokenizer, silently (§0) |
| Multiplier, digit-first | `2x`, `2X`, `2х` (Cyrillic х U+0445), `2×` (U+00D7), `2*` | The measured failure |
| Multiplier, sign-first | `x2`, `×2`, `х2`, `*2` | |
| Number glued to unit | `200g`, `200г`, `2tbsp`, `2шт`, `500ml` | `[REPO]` partially handled — only when the suffix is in `QUANTITY_WORDS` |
| Range | `2-3`, `2–3` | Hyphen must not be a separator; take the lower bound or ask. `[KNOWLEDGE]` |

The three multiplier glyphs `x` / `х` / `×` are the same intent in three
codepoints. Latin `x` U+0078, Cyrillic `х` U+0445 and the multiplication sign
U+00D7 all reach a Russian keyboard trivially, and a Russian user typing on a
Cyrillic layout produces `х2` with the Cyrillic letter without noticing. Fold
the three before matching. `[KNOWLEDGE]`

### 3.2 English — additions to the shipped set

The shipped set is at §0. Missing, all `[KNOWLEDGE]` unless marked:

```
couple, a couple of, pair, a pair of, few, a few, several, handful, a handful of,
pinch, dash, splash, knob, drizzle, scoop, dollop,
third, thirds, halves, quarters, whole,
eleven, twelve, dozen, twenty, fifty, hundred,
slice, slices, sliver, wedge, chunk, chunks, strip, strips, rasher, rashers,
square, squares, stick, sticks, sprig, sprigs, clove, cloves, head, bunch,
can, cans, tin, tins, jar, jars, packet, pack, bag, box, bottle, tub, carton,
plate, bowl, glass, mug
```

`couple`, `slice` and `serving` are **not** in stopwords-iso `en` (verified —
`couple: 0`, `slice: 0`, `serving: 0` matches) `[SW-ISO]`, which is the concrete
reason a stopword list cannot be the quantity list. `few`, `half`, `some`, `of`,
`a`, `an`, `the` all *are* `[SW-ISO]` — which is the concrete reason the two
lists overlap and the ordering in §5 matters.

### 3.3 Russian

Russian has no articles, so the `a / an / the` third of the English list has no
counterpart. Everything below is `[KNOWLEDGE]` — I found no citable source that
covers Russian portion nouns. `pymorphy3` + OpenCorpora (flagged in the prior
research as a build-time paradigm generator, MIT code / CC BY-SA data) is the
right way to expand each lemma into its case forms; the lists below are lemmas
plus the forms I am confident get typed, and should be treated as the *seed* for
that generator rather than as the final table.

**Cardinal numerals** (nominative, plus the oblique forms that show up after
prepositions):

```
один, одна, одно, одного, одну,
два, две, двух, двум,
три, трёх, трех, трем, трём,
четыре, четырёх, четырех,
пять, пяти, шесть, шести, семь, семи, восемь, восьми, девять, девяти,
десять, десяти, одиннадцать, двенадцать, тринадцать, четырнадцать, пятнадцать,
шестнадцать, семнадцать, восемнадцать, девятнадцать,
двадцать, тридцать, сорок, пятьдесят, шестьдесят, семьдесят, восемьдесят,
девяносто, сто, ста, двести, триста, четыреста, пятьсот, шестьсот, семьсот,
восемьсот, девятьсот, тысяча
```

**Collective numerals** — `двое, трое, четверо, пятеро`. Note that these are
grammatically wrong with most food nouns (`два яйца`, not `двое яиц`), so they
are low-frequency but harmless to accept.

**Fractions and halves:**

```
половина, половину, половины, половинка, половинку,
пол, пол-, полтора, полторы, полутора,
четверть, четверти, четвертинка,
треть, трети, две трети
```

**`пол` is the prefix trap.** It attaches directly (`полбанки`, `полстакана`,
`пол-литра`) and it is also the first three letters of **`полба`** (spelt, a
grain — a real catalog food) and of `полбы`, `полбяной`. Whole-token matching is
what keeps them apart; a prefix rule for `пол-` would delete the food. The same
argument covers `сок` (juice) against `с`/`со`, and `судак`, `сыр`, `сельдь`,
`сметана` — every one of which begins with the letter that must never be a
separator.

**Vague quantities:**

```
пара, пары, парочка, несколько, немного, чуть, чуть-чуть, чуток,
горсть, горстка, щепотка, щепоть, капля, капелька, ложечка
```

`пара` is **not** in stopwords-iso `ru` (verified: `пара: 0`) `[SW-ISO]`, so
again the stopword list cannot stand in for the quantity list.

**Portion nouns** (lemma — commonly typed forms):

```
штука — штуки, штук, шт, шт.
кусок — куска, куски, кусков
кусочек — кусочка, кусочки, кусочков
ломтик — ломтика, ломтики, ломтиков
ломоть — ломтя
долька — дольки, долек
зубчик — зубчика (garlic)
пучок — пучка (bunch)
веточка — веточки (sprig)
головка — головки (head)
порция — порции, порций
тарелка — тарелки
миска — миски
чашка — чашки, чашек
кружка — кружки
стакан — стакана, стаканов
банка — банки, банок
пачка — пачки
упаковка — упаковки
пакет — пакета
бутылка — бутылки
ложка — ложки, ложек
половник, черпак
```

### 3.4 Spanish

Everything `[KNOWLEDGE]` unless marked.

**Articles and determiners** (all `[SW-ISO]`):

```
el, la, los, las, un, una, unos, unas, del, al, lo
```

**Cardinal numerals:**

```
uno, un, una, dos, tres, cuatro, cinco, seis, siete, ocho, nueve, diez,
once, doce, trece, catorce, quince, dieciséis, dieciseis, diecisiete,
dieciocho, diecinueve, veinte, veintiuno, veintidós, treinta, cuarenta,
cincuenta, sesenta, setenta, ochenta, noventa, cien, ciento, doscientos,
trescientos, quinientos, mil, docena, media docena
```

**Fractions and halves:**

```
medio, media, mitad, la mitad de,
cuarto, cuarta, un cuarto de, tercio, un tercio de, entero, entera
```

**`media` is the Spanish `half and half`.** `media luna` and `medialuna` are an
Argentine croissant — a food. Stripping a leading `media` turns the food into
`luna`. `cuarto` has the same shape in `cuarto de libra`. Both are covered by
edge-only whole-token stripping *plus* the protected-phrase index; neither is
covered by edge-only stripping alone, because `media luna` is exactly a leading
quantity word followed by a noun.

**Vague quantities:**

```
un par de, par, unos, unas, algunos, algunas, varios, varias,
un poco de, poco, poquito, un puñado de, puñado, una pizca de, pizca,
un chorrito de, chorrito, bastante
```

**Portion nouns:**

```
rebanada, rebanadas        (bread slice)
rodaja, rodajas            (round slice)
loncha, lonchas, lonja     (thin slice, ham/cheese)
trozo, trozos, pedazo, pedazos
pieza, piezas, unidad, unidades
porción, porciones, ración, raciones
plato, platos, tazón, tazones, bol, cuenco
taza, tazas, vaso, vasos, copa, copas
lata, latas, bote, botes, paquete, bolsa, botella, tarro
puñado, pizca, diente, dientes   (diente de ajo)
gajo, gajos, ramita, ramitas, barra, hogaza
```

---

## 4. Units

CLDR is the source for the canonical name and the short form. It is **not** the
source for what users type — CLDR gives one abbreviation per unit per width, and
real recipe register has several. The non-CLDR rows are marked.

### 4.1 English `[CLDR]` (`en`, CLDR 48, long → short → narrow)

| Unit | long (one / other) | short | narrow |
| --- | --- | --- | --- |
| gram | gram / grams | `g` | `g` (glued: `{0}g`) |
| milligram | milligram / milligrams | `mg` | `mg` |
| kilogram | kilogram / kilograms | `kg` | `kg` |
| ounce | ounce / ounces | `oz` | `oz` |
| pound | pound / pounds | `lb` | `lb` |
| liter | liter / liters | `L` | `L` |
| milliliter | milliliter / milliliters | `mL` | `mL` |
| deciliter | deciliter / deciliters | `dL` | `dL` |
| cup | cup / cups | **`c`** | `c` |
| tablespoon | tablespoon / tablespoons | `tbsp` | `tbsp` |
| teaspoon | teaspoon / teaspoons | `tsp` | `tsp` |
| fluid ounce | fluid ounce / fluid ounces | `fl oz` | `fl oz` |
| pint / quart / gallon | … | `pt` / `qt` / `gal` | same |

**Do not ship CLDR's `c` for cup.** A bare single Latin `c` as a unit token is
one keystroke from noise and, in a union vocabulary, from the stopwords-iso
Russian artifact in §4.4. `[KNOWLEDGE]`

Non-CLDR forms users type: `lbs`, `ozs`, `T` / `Tbs` / `Tbsp` for tablespoon
(and lowercase `t` for teaspoon — an ambiguity worth *not* resolving silently).
`[KNOWLEDGE]`

### 4.2 Spanish `[CLDR]` (`es`)

| Unit | long (one / other) | short | narrow (glued) |
| --- | --- | --- | --- |
| gramo | gramo / gramos | `g` | `{0}g` |
| miligramo | miligramo / miligramos | `mg` | `{0}mg` |
| kilogramo | kilogramo / kilogramos | `kg` | `{0}kg` |
| onza | onza / onzas | `oz` | `{0}oz` |
| libra | libra / libras | `lb` | `{0}lb` |
| litro | litro / litros | `l` | `{0}l` |
| mililitro | mililitro / mililitros | `ml` | `{0} ml` |
| decilitro | decilitro / decilitros | `dl` | `{0}dl` |
| centilitro | centilitro / centilitros | `cl` | `{0}cl` |
| taza | taza / tazas | `tza` | `{0}tza` |
| cucharada | cucharada / cucharadas | `cda` | `{0}cda` |
| cucharadita | cucharadita / cucharaditas | `cdta` | `{0}cdta` |
| onza líquida | onza líquida / onzas líquidas | `fl oz` | `fl oz` |

Non-CLDR forms `[KNOWLEDGE]`: `gr`, `grs`, `grms` (very common for gramo and
not CLDR-sanctioned), `kgs`, `cda.`, `cdta.`, `cs` (cucharada sopera), `cp`
(cucharada de postre), `cc` (centímetro cúbico, used for ml). **`c/s` means
*cantidad suficiente*, not a unit** — do not add it.

### 4.3 Russian `[CLDR]` (`ru`)

Russian needs the plural category, not just singular/plural: CLDR `ru` gives
`one` / `few` / `many` / `other` and they are different words.

| Unit | one | few | many | short `[CLDR]` |
| --- | --- | --- | --- | --- |
| грамм | грамм | грамма | грамм | `г` |
| миллиграмм | миллиграмм | миллиграмма | миллиграмм | `мг` |
| килограмм | килограмм | килограмма | килограмм | `кг` |
| литр | литр | литра | литров | `л` |
| миллилитр | миллилитр | миллилитра | миллилитров | `мл` |
| децилитр | децилитр | децилитра | децилитров | `дл` |
| столовая ложка | столовая ложка | столовые ложки | столовых ложек | `ст. л.` |
| чайная ложка | чайная ложка | чайные ложки | чайных ложек | `ч. л.` |
| чашка | амер. чашка | амер. чашки | амер. чашек | `ам. чаш.` |
| унция | унция | унции | унций | `унц.` |
| фунт | фунт | фунта | фунтов | `фнт` |

Non-CLDR forms `[KNOWLEDGE]`, and these are the ones that actually get typed:

```
гр, гр., г., грамм, граммов, грамма, грамма́
кг., мл., л.
ст.л., ст. ложка, ст ложка, столов. ложка
ч.л., ч. ложка, чайн. л.
стакан, ст.   ← see the warning below
```

Note CLDR's `many` for gram is `грамм`, but **`граммов`** is at least as common
in typed Russian and CLDR does not carry it. Add it.

Three Russian unit traps:

1. **`ст.` is ambiguous.** It abbreviates both `столовая ложка` (~15 ml) and
   `стакан` (~200–250 ml) — a 15× difference. This must not be resolved
   silently; it is exactly the "never silently default" case in AGENTS.md. Rule:
   `ст. л.` / `ст.л.` → tablespoon; bare `ст.` before a food → surface both and
   let the user pick. `[KNOWLEDGE]`
2. **CLDR's Russian short forms contain periods and spaces** (`ст. л.`,
   `ч. л.`, `ам. чаш.`). The shipped tokenizer splits on any non-alphanumeric
   `[REPO]`, so `ст. л.` becomes the two tokens `ст` and `л`. The unit
   vocabulary must therefore be matched as a **token sequence**, not as a single
   token — or the periods must be collapsed before tokenizing. The current
   `isQuantityToken` single-token design cannot express this.
3. **`г` and `л` are single Cyrillic letters** and also abbreviate `год`,
   `город`, `лист`. They are safe *as a trailing token after a number*
   (`200 г риса`) and unsafe as free-standing vocabulary. Only accept them when
   they directly follow a numeral. `[KNOWLEDGE]`

### 4.4 The homoglyph problem

`normalizeForSearch` folds Latin diacritics and passes Cyrillic through
untouched `[REPO]`. Latin and Cyrillic share a large set of identical-looking
letters — `а/a е/e о/o р/p с/c у/y х/x к/k м/m т/t в/b н/h` — and a bilingual
user with two keyboards produces mixed-script tokens constantly.

Two concrete consequences:

- A user typing `рис c овощами` with a **Latin** `c` gets a token that no
  Russian rule recognises. If `с` is on the do-not-split list and `c` is not,
  the protection silently does not apply.
- **stopwords-iso `ru` ships this bug itself.** Its first entry is the Latin
  letter `c` (U+0063) — verified by codepoint — alongside the correct Cyrillic
  `с` (U+0441) `[SW-ISO]`. Pasting the list wholesale installs a rule keyed on a
  Latin `c`, which in a union vocabulary is also CLDR's abbreviation for *cup*
  (§4.1). Strip that entry, and audit any other list the same way.

Recommendation: extend the normalizer with a Cyrillic→Latin homoglyph fold
applied **only for vocabulary lookup**, never for the text shown back to the
user as provenance. `[KNOWLEDGE]`

---

## 5. Ordering — which vocabulary runs before which, and why

The pipeline, in order. Each step names what breaks if it is moved.

**Step 0 — select the vocabulary set.**
Keyed on the market's *accepted input languages* from the language matrix, not
on the catalog language: `search-mechanics.md` already establishes that a
Russian speaker in the US market types Cyrillic against English records
`[REPO]`. In practice this means a **union** of vocabularies is active, which is
only safe if the collisions are audited. The audited collisions across en/es/ru
are: `y` (es separator, no en/ru clash), `e` (es, single letter), `a` (en
quantity vs es preposition), `con` / `с` (never separators), `i` (transliterated
`и` vs the English pronoun **I** — excluded, §1.7), `c` (CLDR cup vs the
stopwords-iso artifact — excluded, §4.4), `media` and `cuarto` (es quantity vs
`media luna` / `cuarto de libra`).

**Step 1 — case fold + diacritic fold + homoglyph fold.**
Before anything else, because every later match is on folded text. **Must not
delete `×`, `½`, `/` or `-` at this stage** — steps 3 and 4 need them. The
current `normalizeForSearch` is safe here (it only substitutes); the current
`tokenizeNormalized` is not (it deletes them), which is why step 3 runs on the
folded *string*, not on tokens.

**Step 2 — protected-phrase scan (do-not-split).**
Longest-match-first over the protected list plus the catalog's own dish names.
**Must precede step 3.** If separators run first, `arroz con leche` is already
cut in two and no later step can put it back — the span provenance the review UI
shows would name two items the user never typed.

**Step 3 — separator scan.**
Strong separators only. Weak separators are recorded as *candidate* boundaries
and not applied. Runs on the folded string, before tokenization, so that `/`
inside `1/2` and `-` inside `пол-литра` are still visible as characters rather
than already erased.

**Step 4 — per fragment: multiplier / fraction / numeral match.**
**Must precede step 6.** This is the ordering the prior research already found:
`a couple of eggs` only resolves if `couple of` is matched as a quantity before
stopword removal takes `of`. The same trap exists in both other languages and is
worse, because the stranded function word is more common:
- `un par de huevos` — `de` is a stopword `[SW-ISO]`; strip it first and `par`
  is orphaned from its complement.
- `unos cuantos huevos` — `unos` is a stopword.
- `пара яиц` survives (`пара` is not a Russian stopword `[SW-ISO]`), but
  `немного риса` does not: `немного` is adverbial and reachable from a stopword
  list.

**Step 5 — unit attachment.**
Attach a unit to the numeral found in step 4, including glued forms (`200г`) and
**multi-token abbreviations** (`ст. л.` → `ст` + `л`, §4.3). Must follow step 4
because the unit is only a unit in the presence of a number — that is what makes
bare `г` and `л` safe.

**Step 6 — portion-noun capture, then stopword removal.**
Portion nouns first (`ломтик`, `rebanada`, `slice`) because several of them are
also plain nouns and a stopword list will not distinguish them. Stopword removal
last, and **edge-only, whole-token** — never interior, never prefix. The three
reasons, one per language: `half and half` `[REPO]`, `media luna`, `полба`.

**Step 7 — preparation words.**
Unchanged from today: kept and reported, never stripped `[REPO]`.

**Step 8 — match, then relax.**
Existing cascade: exact → quantity-stripped → preparation-relaxed `[REPO]`. Add
one final rung: **weak-separator split**, using the candidates recorded in step
3, attempted only when everything above returned nothing.

### The two orderings that are non-negotiable

1. **Protected phrases before separators** — otherwise `arroz con leche`,
   `кофе с молоком` and `café con leche` are already destroyed.
2. **Quantity before stopwords** — otherwise `a couple of eggs`, `un par de
   huevos` and `unos cuantos huevos` lose the word that binds the quantity to
   the food.

Everything else in the list can be argued about. These two cannot: each one has
a measured or named failure attached.

---

## 6. Concrete test cases

Format: input → expected fragments (`|` separates fragments) → expected quantity
extraction → rule. `→ 1 fragment` means the whole string stays together.

### 6.1 The three measured regressions

| # | Input | Lang | Expected | Rule |
| --- | --- | --- | --- | --- |
| 1 | `курица и рис` | ru | `курица` \| `рис` | §1.3 `и` is a strong separator |
| 2 | `arroz y frijoles negros` | es | `arroz` \| `frijoles negros` | §1.5 `y` is a strong separator |
| 3 | `2x eggs` | en | 1 fragment, qty `2`, food `eggs` | §3.1 digit-first multiplier |

### 6.2 Do-not-split — Spanish

| # | Input | Expected | Rule |
| --- | --- | --- | --- |
| 4 | `arroz con leche` | 1 fragment | §2.1 `con` never splits |
| 5 | `arroz con pollo` | 1 fragment | §2.1 — and both halves are catalog foods, so a naive split *succeeds* and returns a wrong meal. This is the case that must not merely miss. |
| 6 | `café con leche` | 1 fragment | §2.1 + §2.6(b): not in the seed catalog, so whole-fragment-first alone does not save it — needs the protected list |
| 7 | `pollo con arroz` | 1 fragment, resolves `dish:chicken-and-rice` | §2.1 against an existing seed record `[REPO]` |
| 8 | `moros y cristianos` | 1 fragment | §2.2 `y` has exceptions too |
| 9 | `sándwich de jamón y queso` | 1 fragment | §2.2 `y` inside a `de`-complement |
| 10 | `pechuga de pollo` | 1 fragment | §2.3 `de` never splits; existing seed alternate `[REPO]` |
| 11 | `pollo a la plancha` | 1 fragment, no quantity stripped | §2.3 + §3.4 — `a` and `la` are stopwords but interior, and edge-only stripping must leave them |
| 12 | `yogur sin azúcar` | 1 fragment, `sin azúcar` retained | §2.3 negation — splitting **adds** sugar |
| 13 | `atún en aceite` | 1 fragment | §2.3 — `en aceite` vs `en agua` moves fat |

### 6.3 Do-not-split — Russian

| # | Input | Expected | Rule |
| --- | --- | --- | --- |
| 14 | `кофе с молоком` | 1 fragment | §2.4 `с` never splits |
| 15 | `рис с овощами` | 1 fragment | §2.4 |
| 16 | `курица с рисом` | 1 fragment, resolves `dish:chicken-and-rice` | §2.4 against an existing seed record `[REPO]` |
| 17 | `пельмени со сметаной` | 1 fragment | §2.4 `со` is a separate entry, not derived from `с` |
| 18 | `сельдь под шубой` | 1 fragment | §2.5 `под` |
| 19 | `яйцо на ржаном хлебе` | 1 fragment, resolves `dish:egg-on-rye` | §2.5 `на`; existing seed alternate `[REPO]` |
| 20 | `каша на молоке` | 1 fragment | §2.5 — vs `каша на воде`, a real nutrition difference |
| 21 | `кофе без сахара` | 1 fragment, `без сахара` retained | §2.5 negation — splitting **adds** sugar |
| 22 | `мясо по-французски` | 1 fragment | §1.1 hyphen is never a separator |

### 6.4 Do-not-split — English (the symmetry case)

| # | Input | Expected | Rule |
| --- | --- | --- | --- |
| 23 | `macaroni with cheese` | 1 fragment | §1.2 — **fails today**: `with` is a shipped strong separator `[REPO]` |
| 24 | `chili with beans` | 1 fragment | §1.2 same |
| 25 | `chicken and rice` | `chicken` \| `rice` | §1.2 — `and` must keep splitting after `with` is demoted |
| 26 | `half and half` | 1 fragment, nothing stripped | `[REPO]` existing behaviour must survive the new vocabulary |
| 27 | `coffee without sugar` | 1 fragment, `without sugar` retained | §2.6(c) negation |

### 6.5 Quantity and ordering

| # | Input | Lang | Expected | Rule |
| --- | --- | --- | --- | --- |
| 28 | `a couple of eggs` | en | qty `2`, food `eggs` | §5 step 4 before step 6 — the prior research's own case |
| 29 | `un par de huevos` | es | qty `2`, food `huevos` | §5 same trap, `de` is the stranded stopword |
| 30 | `unos cuantos huevos` | es | vague qty, food `huevos` | §5 — `unos` is a stopword |
| 31 | `пара яиц` | ru | qty `2`, food `яйца` | §3.3 — `пара` is *not* in stopwords-iso `[SW-ISO]` |
| 32 | `два вареных яйца и ломтик ржаного хлеба` | ru | `два вареных яйца` \| `ломтик ржаного хлеба`; qty `2` + `1 ломтик` | §1.3 + §3.3 portion noun |
| 33 | `2 ст. л. оливкового масла` | ru | qty `2`, unit tablespoon, food `оливковое масло` | §4.3 trap 2 — multi-token unit abbreviation |
| 34 | `200г риса` | ru | qty `200 g`, food `рис` | §3.1 glued unit, Cyrillic |
| 35 | `1 ст. сахара` | ru | **ambiguous** — surface tablespoon *and* glass, do not default | §4.3 trap 1 |
| 36 | `х2 яйца` | ru | qty `2`, food `яйца` | §3.1 — Cyrillic `х` U+0445 |
| 37 | `eggs x2` | en | qty `2`, food `eggs` | §3.1 sign-first multiplier |
| 38 | `½ aguacate` | es | qty `0.5`, food `aguacate` | §3.1 — currently the glyph is silently dropped (§0) |
| 39 | `1/2 aguacate y 2 huevos` | es | `1/2 aguacate` \| `2 huevos` | §1.1 `/` is not a separator; `y` is |
| 40 | `1,5 л молока` | ru | 1 fragment, qty `1.5 L` | §3.1 — decimal comma vs `,` as separator |
| 41 | `2 rebanadas de pan` | es | qty `2` slices, food `pan` | §3.4 portion noun + §2.3 `de` |
| 42 | `media luna` | es | 1 fragment, **nothing stripped** | §3.4 — the Spanish `half and half` |
| 43 | `medio aguacate` | es | qty `0.5`, food `aguacate` | §3.4 — must still work after case 42 |
| 44 | `полба` | ru | 1 fragment, nothing stripped | §3.3 — `пол-` must not be a prefix rule |
| 45 | `пол-литра молока` | ru | qty `0.5 L`, food `молоко` | §3.3 + §1.1 hyphen |

### 6.6 Mixed — both rules in one string

These are the highest-value regressions: each needs the protected list *and* the
separator list to be right simultaneously.

| # | Input | Lang | Expected | Rule |
| --- | --- | --- | --- | --- |
| 46 | `yogur con granola y frutas` | es | `yogur con granola` \| `frutas` | `con` holds, `y` splits |
| 47 | `курица с рисом и салат` | ru | `курица с рисом` \| `салат` | `с` holds, `и` splits |
| 48 | `arroz con leche y café` | es | `arroz con leche` \| `café` | longest-protected-match before separator |
| 49 | `кофе с молоком и бутерброд с сыром` | ru | `кофе с молоком` \| `бутерброд с сыром` | two protected phrases either side of one separator |
| 50 | `2 huevos con jamón y una tostada` | es | `2 huevos con jamón` \| `una tostada`; qty `2` and `1` | quantity + protected + separator in one string |

### 6.7 Cross-language and script traps

| # | Input | Lang | Expected | Rule |
| --- | --- | --- | --- | --- |
| 51 | `I had two eggs and toast` | en | `I had two eggs` \| `toast`, qty `2` | §1.7 — proves Latin `i` was **not** added as a Russian separator |
| 52 | `kuritsa i ris` | ru-Latn | 1 fragment (documented limitation) | §1.7 — transliterated Russian does not split on `i` |
| 53 | `рис c овощами` (Latin `c`) | ru | 1 fragment | §4.4 homoglyph fold must reach the do-not-split rule |
| 54 | `boiled egg\nand rye bread` | en | `boiled egg` \| `rye bread` | `[REPO]` existing newline behaviour must survive |
| 55 | `курица, рис` | ru | `курица` \| `рис` | comma works in Cyrillic today; guard against a regression when the decimal-comma rule (case 40) lands |
| 56 | `щи да каша` | ru | 1 fragment | §1.4 — `да` is not a separator |
| 57 | `да, курица` | ru | `курица` (or 1 fragment) — **never** split on `да` as a conjunction | §1.4 — `да` = *yes* |
| 58 | `peras e higos` | es | `peras` \| `higos` | §1.6 — `e` before `hi-` |
| 59 | `pan e integral` | es | 1 fragment | §1.6 — `e` only when followed by `i-`/`hi-`; here it precedes a modifier, not a second food. **This one I am least sure of and it should be reviewed by a native speaker before it is written as a test.** |

---

## 7. What is not settled

Stated plainly so nobody reads this document as finished.

1. **Every `[KNOWLEDGE]` list needs a native-speaker pass.** The Russian portion
   nouns and the Spanish dish list are where I am most exposed: they are recalled,
   not extracted from a corpus, and a wrong entry in the do-not-split list is
   invisible (it only ever *prevents* a split) while a missing entry is a
   user-visible wrong meal.
2. **The do-not-split lists have no citable source and probably cannot have
   one.** No licence-clean, machine-readable list of Spanish or Russian dish
   names exists — that is the same gap the prior research hit on the dish layer,
   and the Russian normative recipe collections that would close it have an
   unresolved licence. The mitigation is mechanism, not vocabulary: §2.6(a)
   whole-fragment-first means the list only has to cover what the catalog does
   not, and it shrinks as the catalog grows.
3. **Russian case forms should be generated, not enumerated.** §3.3 is a seed
   for a `pymorphy3`/OpenCorpora build-time step, and that step carries CC BY-SA
   onto the generated table — a live licence question from the prior research,
   not a settled one.
4. **English `with` demotion is a behaviour change** to shipped code with
   existing tests. Cases 23–25 will contradict whatever asserts today's
   behaviour; that is intended, and the existing test should be read before the
   new one is written.
5. **`ст.` (case 35) has no correct silent answer.** It needs a UI decision, not
   a parser decision.
6. **Cases 51–53 assume a homoglyph fold that does not exist yet.** They are
   specifications for behaviour, not descriptions of it.
