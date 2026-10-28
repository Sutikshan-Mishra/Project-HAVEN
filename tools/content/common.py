# SPDX-License-Identifier: GPL-3.0-or-later
"""Shared helpers and reference list for the bundled guide content.

References name the publisher and document. URLs are included only where the page is a
long-standing public page; every reference must be checked during review
(docs/CONTENT_REVIEW.md). Do not add a citation you have not read.
"""

UPDATED = "2026-10-28"


def ref(title, publisher, url=None):
    r = {"title": title, "publisher": publisher}
    if url:
        r["url"] = url
    return r


def NHS(title, path):
    return ref(title, "NHS (UK)", f"https://www.nhs.uk/conditions/{path}/")


IFRC = ref("International First Aid, Resuscitation and Education Guidelines 2020",
           "International Federation of Red Cross and Red Crescent Societies")
AHA = ref("Guidelines for CPR and Emergency Cardiovascular Care", "American Heart Association",
          "https://cpr.heart.org/en/resuscitation-science/cpr-and-ecc-guidelines")
ERC = ref("European Resuscitation Council Guidelines (Basic Life Support and First Aid)",
          "European Resuscitation Council")
WHO_SNAKE = ref("Snakebite envenoming fact sheet", "World Health Organization",
                "https://www.who.int/news-room/fact-sheets/detail/snakebite-envenoming")
WHO_RABIES = ref("Rabies fact sheet", "World Health Organization",
                 "https://www.who.int/news-room/fact-sheets/detail/rabies")
WHO_WATER = ref("Drinking-water fact sheet", "World Health Organization",
                "https://www.who.int/news-room/fact-sheets/detail/drinking-water")
WHO_GDWQ = ref("Guidelines for Drinking-water Quality", "World Health Organization")
WHO_FOOD = ref("Five Keys to Safer Food Manual", "World Health Organization")
CDC_WATER = ref("Making Water Safe in an Emergency", "US Centers for Disease Control and Prevention")
SPHERE = ref("The Sphere Handbook: Water supply, sanitation and hygiene", "Sphere Association",
             "https://spherestandards.org/handbook/")
NDMA = ref("Disaster awareness guidance (Do's and Don'ts)", "National Disaster Management Authority, India",
           "https://ndma.gov.in")
NCDC_HEAT = ref("National Action Plan on Heat Related Illnesses",
                "National Centre for Disease Control, Ministry of Health and Family Welfare, India")
READY = ref("Build a Kit and Make a Plan", "Ready.gov (US Federal Emergency Management Agency)",
            "https://www.ready.gov/kit")
FSSAI = ref("Consumer guidance on food safety", "Food Safety and Standards Authority of India",
            "https://www.fssai.gov.in")
ICAO_SIGNALS = ref("Ground-to-air visual signal code (Annex 12, Search and Rescue)",
                   "International Civil Aviation Organization")
USDA_FOOD = ref("Food Safety in a Power Outage and Flood", "US Department of Agriculture, Food Safety and Inspection Service")

STYLES = {"steps", "bullets", "checklist", "avoid", "warning", "text"}


def art(id, title, summary, keywords, sections, refs=(), updated=UPDATED):
    out_sections = []
    for sid, stitle, style, items in sections:
        assert style in STYLES, style
        assert items, f"{id}/{sid} is empty"
        out_sections.append({"id": sid, "title": stitle, "style": style, "items": list(items)})
    return {
        "id": id,
        "title": title,
        "summary": summary,
        "keywords": list(keywords),
        "sections": out_sections,
        "references": list(refs),
        "updated": updated,
        "review": {"status": "pending"},
    }
