# Attribution -- food synonym table

<!-- GENERATED FILE. Produced by scripts/generate-food-synonyms.py. Do not edit by hand. -->

`food-synonyms.json` in this directory is generated at build time from two
upstream sources with **different licences and different obligations**. They are
listed separately because merging them would misstate both.

## AGROVOC -- attribution REQUIRED

> Contains information from the **AGROVOC Multilingual Thesaurus**, (c) Food and
> Agriculture Organization of the United Nations (FAO), licensed under
> **CC-BY IGO 3.0**.

- Source: <https://agrovoc.fao.org/>
- Licence: **CC-BY IGO 3.0** -- <https://creativecommons.org/licenses/by/3.0/igo/>
- Licence verified 2026-08-02 against FAO's own FAQ,
  <https://aims.fao.org/standards/agrovoc/faq>, not against a secondary summary.
- Used for: canonical concept ids, the food subset hierarchy, and display names
  in English, Spanish and Russian.

**This credit is a licence condition.** It must appear in the shipped
application's licences/acknowledgements screen. Removing it breaks the licence.

Two caveats recorded rather than assumed away:

- FAO holds copyright for AGROVOC content in its six official languages. English,
  Spanish and Russian are all inside that set, so this extract sits under a
  single licence. Content in **other** languages is copyright of the authoring
  institutions and may carry different terms -- adding a fourth language is a
  rights question, not just a product one.
- Some third-party summaries now describe AGROVOC as CC-BY 4.0 while FAO's FAQ
  still says CC-BY IGO 3.0. Both are attribution-only with no ShareAlike, so the
  obligation discharged above is the same either way, but the discrepancy should
  be confirmed with counsel before release.

## Wikidata -- CC0, no obligation

- Source: <https://www.wikidata.org/>
- Licence: **CC0 1.0 Universal** -- <https://creativecommons.org/publicdomain/zero/1.0/>
- Used for: colloquial and regional alternate names (the words people actually
  type) joined to AGROVOC via property **P8061 (AGROVOC ID)**.

Wikidata's CC0 dedication imposes **no** attribution requirement. The credit
above is voluntary good practice and may be reworded freely, unlike the AGROVOC
credit.

## Not used

Open Food Facts taxonomies were evaluated and **rejected**: they are ODbL, whose
share-alike terms would propagate to this derived table. Nothing in this
directory derives from an ODbL source.
