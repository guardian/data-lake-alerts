# data-lake-alerts

Allows developers to detect production issues, by automating the process of querying the Data Lake 
and alerting on undesirable results.

## Adding a new alert

###Before you start:

You will need to be able to run queries against the Data Lake using `AWS Athena`, 
so you'll need to request `dataLakeQuerying` permissions via Janus if you do not already have access.

Currently this service has read-only access to the `clean.pageviews` table only. If you need to run queries against 
a different table, you'll need to add further permissions to the Cloudformation template in this repository.

###Adding the alert:

1. Write your query and test it using Athena. 
    1. The Athena pricing model is based on data scanned, so try not to scan more data than necessary.
1. Add `MyFeature extends Feature { ??? }` to the `Features` object. You will need to provide:
    1. A feature id (used when triggering the lambda)
    1. A function which returns your query (to be run by Athena).
    1. A function which performs a check on the query's results and (optionally) 
    additional debug information which will be included in the alert in the event of a failure.
1. Add your feature to `allFeaturesWithMonitoring` (also in the `Features` object).

###Testing your changes

1. Obtain `developerPlayground` and `ophan` Janus credentials.
1. Edit the `TestIt` object, passing in your `Platform` and `Feature`.
1. Run `sbt run`.

###Triggering the monitoring task regularly

1. Add a CloudWatch rule to the repo's CloudFormation template.
    1. The input event should look something like this:
    
    ```
    {
        platform: "android"
        feature: "my_feature_id"    
    }
    ```