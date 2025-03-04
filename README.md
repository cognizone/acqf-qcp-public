# ACQF QCP Documentation

This repository hosts the website for ACQF QCP hosted at https://data.acqf-qcp.africa.

## Development
The data model diagram can be generated as follows

``` bash
SHACLVIZ=shaclviz.jar
if ! [ -e "$SHACLVIZ" ]; then
    curl -L -o $SHACLVIZ https://repo1.maven.org/maven2/zone/cogni/semanticz/semanticz-shaclviz/1.0.2/semanticz-shaclviz-1.0.2-executable.jar
fi
java -jar $SHACLVIZ file:docs/model/exchange-model.ttl docs/exchange-model/exchange-model.tgf --fieldQuery=docs/exchange-model/fields-acqf.rq --outputFormat tgf
```