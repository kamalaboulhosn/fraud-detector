This repo contains the code for a LLMAgent written with the Google [Agent
Development Kit](https://google.github.io/adk-docs/) designed to check for
fradulent credit card transactions. The system consists of two components:

1. A data generator that creates fake credit card transactions
2. A Cloud Run job that response to an HTTP endpoint and at /message and runs
   a Gemini-based LLM on it. See the [instructions to the model](https://github.com/kamalaboulhosn/fraud-detector/blob/2da72784427bb385de8dd91605d51d1e14299204/fraud_service/src/main/java/agents/fraudagent/FraudAgent.java#L58-L72).

The agent publishes messages to Cloud Pub/Sub. It publishes every analyized
transaction to one topic and then any cards deemed compromised to another. The
general flow of data looks like this:

![Architecture Diagram](https://github.com/kamalaboulhosn/fraud-detector/blob/main/fraud_flow.png?raw=true)

## Setup

Follow these instructions to deploy and run the model.

### Running the agent

1. Update [instructions 3 and 4](https://github.com/kamalaboulhosn/fraud-detector/blob/2da72784427bb385de8dd91605d51d1e14299204/fraud_service/src/main/java/agents/fraudagent/FraudAgent.java#L67-L68)
   and replace the two `<INSERT TOPIC HERE>`s with different Pub/Sub topics to
   use. They should be the full path to the topics, e.g.,
   `projects/<my project>/topics/<my topic>`.

2. Run the following commands from the `fraud_service` subdirectory to build and
   deploy the model:
   ```bash
   export GOOGLE_GENAI_USE_VERTEXAI=True
   export GOOGLE_CLOUD_LOCATION=REGION
   export GOOGLE_CLOUD_PROJECT=PROJECT
   gcloud run deploy fraud-detector  --source . --region $GOOGLE_CLOUD_LOCATION --project $GOOGLE_CLOUD_PROJECT --allow-unauthenticated --set-env-vars="GOOGLE_CLOUD_PROJECT=$GOOGLE_CLOUD_PROJECT,GOOGLE_CLOUD_LOCATION=$GOOGLE_CLOUD_LOCATION,GOOGLE_GENAI_USE_VERTEXAI=$GOOGLE_GENAI_USE_VERTEXAI"
   ```
   Replace `REGION` with the Cloud region in which you want to run (e.g.,
   `us-central1`) and replace `PROJECT` with the Cloud project where you want
   the model to run. When the commands complete, note the URL given for reaching
   the Cloud Run job.

### Create the BigQuery tables

The demo requires two BigQuery tables. You probably want to locate these in the
same region as the agent (or a multi-region table in the same continent).

1. Create a BigQuery table for all transactions with the following schema:
   ```json
      [
     {
       "mode": "NULLABLE",
       "name": "credit_card_number",
       "type": "STRING"
     },
     {
       "mode": "NULLABLE",
       "name": "receiver",
       "type": "STRING"
     },
     {
       "mode": "NULLABLE",
       "name": "amount",
       "type": "FLOAT"
     },
     {
       "mode": "NULLABLE",
       "name": "ip_address",
       "type": "STRING"
     },
     {
       "mode": "NULLABLE",
       "name": "fraud_likelihood",
       "type": "FLOAT"
     },
     {
       "mode": "NULLABLE",
       "name": "fraud_reason",
       "type": "STRING"
     },
     {
       "mode": "NULLABLE",
       "name": "timestamp",
       "type": "DATETIME"
     }
   ]
   ```

2. Create another BigQuery table for compromised cards with this schema:
   ```
   [
     {
       "mode": "NULLABLE",
       "name": "credit_card_number",
       "type": "STRING"
     },
     {
       "mode": "NULLABLE",
       "name": "fraud_likelihood",
       "type": "NUMERIC"
     },
     {
       "mode": "NULLABLE",
       "name": "fraud_reason",
       "type": "STRING"
     },
     {
       "mode": "NULLABLE",
       "name": "timestamp",
       "type": "DATETIME"
     }
   ]
   ```

### Create Pub/Sub Topics and Subscriptions.

The demo requires three Pub/Sub topics and subscriptions. You can create the
three topics via gcloud:

```bash
gcloud pubsub topics create raw-transactions
gcloud pubsub topics create augmented-transactions
gcloud pubsub topics create compromised-cards
```

The first topic is the one that appears in the [data generator](https://github.com/kamalaboulhosn/fraud-detector/blob/2da72784427bb385de8dd91605d51d1e14299204/data_generator/src/main/java/data_generator/TransactionGenerator.java#L35). Replace the other two
with the topic names you used in [instructions 3 and 4](https://github.com/kamalaboulhosn/fraud-detector/blob/2da72784427bb385de8dd91605d51d1e14299204/fraud_service/src/main/java/agents/fraudagent/FraudAgent.java#L67-L68).

1. Create a subscription for the `raw-transactions` topic:
   ```bash
   gcloud pubsub subscriptions create raw-transactions-sub --topic raw-transactions --ack-deadline=600 --push-endpoint=<ENDPOINT> --push-no-wrapper    --push-auth-service-account=<SERVICE_ACCOUNT>
   ```
   Replace `ENDPOINT` with `<CLOUD_RUN_ENDPOINT>/message` where
   `CLOUD_RUN_ENDPOINT` is the endpoint that printed out when you created the
   Cloud Run job. Replace `SERVICE_ACCOUNT` with a service account you create in
   the same project. This will be the credentials used to call the Cloud Run
   job.

2. Create a BigQuery subscription for the `augmented-transactions` topic:
   ```bash
   gcloud pubsub subscriptions create augmented-transactions-sub --topic --augmented-transactions --bigquery-table=<TRANSACTIONS_TABLE> --use-table-schema
   ```
   Replace `TRANSACTIONS_TABLE` with the table ID for the table created above to
   hold all transactions.

3. Create a BigQuery subscription for the `compromised-cards` topic:
   ```bash
   gcloud pubsub subscriptions create compromised-cards-sub --topic --compromised-cards --bigquery-table=<COMPROMISED_TABLE> --use-table-schema
   ```
   Replace `COMPROMISED_TABLE` with the table ID for the table created above to
   hold compromised cards.


### Run the data generator

The data generator is designed to generate transactions to run fraud detection
on. Follow these steps from the `data_generator` subdirectory:

1. Set the project number [in the generator](https://github.com/kamalaboulhosn/fraud-detector/blob/2da72784427bb385de8dd91605d51d1e14299204/data_generator/src/main/java/data_generator/TransactionGenerator.java#L34).
2. If you used a different topic name in place of `raw-transactions`, change the
   topic name [in the generator](https://github.com/kamalaboulhosn/fraud-detector/blob/2da72784427bb385de8dd91605d51d1e14299204/data_generator/src/main/java/data_generator/TransactionGenerator.java#L35).
3. Run the following commands to build and run the generator:
   ```bash
   mvn package
   java -ja': java -jar target/TransactionGenerator.jar
   ```

You should now have the data generator creating transactions and running through
the fraud detection flow.