# mouse-disease-pipeline
Import disease annotations from MGI weekly.

Source file: http://www.informatics.jax.org/downloads/reports/MGI_DO.rpt

Only mouse annotations are processed; human annotations are not loaded to avoid ingesting of data that RGD originally
was submitted to the Alliance, which in turn has been ingested by MGI.

If a potential MGI disease annotation is the same as the similar annotation already in RGD
created by ctd-disease-annotation-pipeline or omim-disease-annotation-pipeline,
it is considered a duplicate and it is skipped from further processing.
