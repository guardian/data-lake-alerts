# data-lake-alerts

Allows developers to detect production issues, by automating the process of querying the Data Lake 
and alerting on undesirable results.

Adding a new alert
------------------

### Before you start

You will need to be able to run queries against the Data Lake using `AWS Athena`, 
so you'll need to request `dataLakeQuerying` permissions via Janus if you do not already have access.

Currently this service has read-only access to the `clean.pageviews` table only. If you need to run queries against 
a different table, you'll need to add further permissions to the Cloudformation template in this repository.

### Adding the alert

1. Write your query and test it using Athena. 
    1. The Athena pricing model is based on data scanned, so try not to scan more data than necessary.
1. Add `MyFeature extends Feature { ??? }` to the `Features` object. You will need to provide:
    1. A feature id (used when triggering the lambda)
    1. A list of platforms which the feature is relevant for (defaults to iOS and Android)
    1. A function which returns your query (to be run by Athena)
    1. A function which performs a check on the query's results and (optionally) 
    additional debug information which will be included in the alert in the event of a failure.
1. Add your feature to `allFeaturesWithMonitoring` (also in the `Features` object).

### Testing your changes

Ensure you are on the correct AWS region (`eu-west-1`). This can be achieved by using the `AWS_REGION` environment variable:

```
export AWS_REGION=eu-west-1
```

1. Obtain `developerPlayground` and `ophan` Janus credentials.
1. Run `sbt "runMain com.gu.datalakealerts.TestWorker my_platform my_feature"` (passing in the relevant `Platform` and `Feature` ids).

Note that when running locally or in the `CODE` environment all alerts will be sent to the `anghammarad.test.alerts` Google Group 
(instead of the team who maintains the specified production stack).

This allows you to send test alerts in these environments without spamming your team.

### Monitoring Schedule

Monitoring checks run at [12:00 UTC every weekday](https://github.com/guardian/data-lake-alerts/blob/master/cfn.yaml#L171). 
To confirm that monitoring has been scheduled correctly for your feature:

1. Run `sbt "runMain com.gu.datalakealerts.TestScheduler"`
    1. Confirm that your feature (and platform) are listed in the output.
