# mouse-disease-annotation-pipeline

Imports disease (DO) annotations from MGI weekly.

## Overview

Downloads the MGI_DO.rpt file from MGI and creates disease annotations for mouse genes in RGD.
Only mouse annotations are processed; human annotations are skipped to avoid re-ingesting
data that RGD originally submitted to the Alliance, which was then ingested by MGI.

## Logic

1. **Download** — fetches MGI_DO.rpt from MGI (gene-to-DO term mappings)
2. **Parse** — reads tab-delimited records with DO term, gene symbol, NCBI Gene ID, and MGI ID
3. **QC** — matches genes by MGI ID or NCBI Gene ID; validates DO terms against RGD ontology.
   Creates IAGP annotations for matched mouse genes and ISS ortholog annotations for
   rat/mouse/human orthologs.
4. **Load** — inserts new annotations; updates last-modified timestamps on existing matches
5. **Delete stale** — removes annotations not seen in the current run, subject to a
   configurable deletion threshold (percentage of original count) as a safety measure

## Logging

- `status` — pipeline progress and summary counters

## Build and run

Requires Java 17. Built with Gradle:
```
./gradlew clean assembleDist
```

