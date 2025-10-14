import sys
from google.adk.agents import LlmAgent
from google.adk.tools import FunctionTool
from google.cloud import pubsub_v1

# --- Define Constants & Globals ---
MODEL_NAME = "gemini-2.5-flash"
# Cache for Pub/Sub publisher clients, one per topic.
_publishers: dict[str, pubsub_v1.PublisherClient] = {}

# --- Define Tool(s) ---

def publish_record(topic: str, json_payload: str) -> dict:
    """Publishes a JSON string to a Google Cloud Pub/Sub topic.

    Args:
        topic: The topic to which to publish.
               (e.g., 'projects/your-project-id/topics/your-topic-id')
        json_payload: The JSON string to publish.
    """
    print(f"PUBLISH {topic} {json_payload}")

    try:
        publisher = _publishers.get(topic)
        if not publisher:
            publisher = pubsub_v1.PublisherClient()
            _publishers[topic] = publisher

        # The publisher client requires data to be bytestrings.
        data = json_payload.encode("utf-8")

        # When you publish a message, the client returns a future.
        future = publisher.publish(topic, data)
        future.result()  # Wait for the publish to complete.

        print("Successfully published!")

    except Exception as e:
        print(f"Could not publish: {e}", file=sys.stderr)
        return {}

    # Return an empty dictionary on success, as in the Java example.
    return {}

# --- Initialize Agent ---

# The Agent should be exposed as a top-level module argument.
root_agent = LlmAgent(
    model=MODEL_NAME,
    name="FraudDetector",
    description="Determines risk of fraud in transactions.",
    global_instruction="""
        You are an agent that is an expert at detecting fraud in financial transactions. You will be given JSON records
        for credit card transactions where you are trying to determine the likelihood of fraud. Possible indicators of fraud:
        - A sequence of transactions for the same credit card using IP addresses from different countries.
        - A sequence of transactions where the first is a small amount of money to a charity and then a large amount of money to a store.
        - Anything else you can find as an expert in fraud detection using resources available to you on the web.

        For each transaction:
        1. Evaluate the likelihood of it being a fradulent transaction and give it a score between 0.0 and 1.0.
        2. Augment the input with two new fields: 'fraud_likelihood' set to this result of this evaluation and 'fraud_reason' with a short description of the reason for the fraud likelihood.
        3. Use "publish_record" to publish this augmented JSON object to the topic projects/<PROJECT>/topics/<TOPIC1>
        4. If the evaluation of fraud from step 1 is > 0.8, use "publish_record" to publish a JSON object containing the timestamp, credit card number, fraud likelihood, and fraud likelihood reason to the topic projects/<PROJECT>/topics/<TOPIC2>.
        5. Return the augmented input from step #3.

        Sample input: {"credit_card_number": "1234567812345678", "receiver": "Macy's", "amount": 100.05, "ip_address": "68.45.25.58", "timestamp":"2025-09-18T11:47:02.814"}
        Sample output: {"credit_card_number": "1234567812345678", "receiver": "Macy's", "amount": 100.05, "ip_address": "68.45.25.58", "timestamp":"2025-09-18T11:47:02.814", "fraud_likelihood": 0.2, "fraud_reason": "Multiple transactions from different countries"}
        """,
    tools=[FunctionTool(publish_record)],
)