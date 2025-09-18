package agents.fraudagent;


import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.InvocationContext;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.LlmAgent.IncludeContents;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.artifacts.InMemoryArtifactService;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.InMemorySessionService;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.FunctionTool;
import com.google.adk.tools.ToolContext;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;

import io.reactivex.rxjava3.core.Maybe;

import com.google.cloud.storage.Storage;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.List;

import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;

public class FraudAgent {

  // --- Define Constants ---
  private static final String MODEL_NAME = "gemini-2.5-flash";
  private static Map<String, Publisher> publishers = new HashMap<>();

  // The Agent should be exposed as a "public static" argument.
  public static final BaseAgent ROOT_AGENT = initAgent();
  // Initialize the Agent in a static class method.
  public static BaseAgent initAgent() {
    FunctionTool publishRecord = FunctionTool.create(FraudAgent.class, "publishRecord");

    BaseAgent fraudAgent = LlmAgent.builder()
        .model(MODEL_NAME)
        .name("FraudDetector")
        .description("Determines risk of fraud in transactions.")
        .instruction(
            """
                You are an agent that is an expert at detecting fraud in financial transactions. You will be given JSON records
                for credit card transactions where you are trying to determine the likelihood of fraud. Possible indicators of fraud:
                - A sequence of transactions for the same credit card using IP addresses from different countries.
                - A sequence of transactions where the first is a small amount of money to a charity and then a large amount of money to a store.
                - Anything else you can find as an expert in fraud detection using resources available to you on the web.

                For each transaction:
                1. Evaluate the likelihood of it being a fradulent transaction and give it a score between 0.0 and 1.0.
                2. Augment the input with two new fields: 'fraud_likelihood' set to this result of this evaluation and 'fraud_reason' with a short description of the reason for the fraud likelihood.
                3. Use "publishRecord" to publish this augmented JSON object to the topic <INSERT TOPIC HERE>
                4. If the evaluation of fraud from step 1 is > 0.8, use "publishRecord" to publish a JSON object containing the timestamp, credit card number, fraud likelihood, and fraud likelihood reason to the topic <INSERT TOPIC HERE>.
                5. Return the augmented input from step #3.

                Sample input: {"credit_card_number": "1234567812345678", "receiver": "Macy's", "amount": 100.05, "ip_address": "68.45.25.58", "timestamp":"2025-09-18T11:47:02.814"}
                Sample output: {"credit_card_number": "1234567812345678", "receiver": "Macy's", "amount": 100.05, "ip_address": "68.45.25.58", "timestamp":"2025-09-18T11:47:02.814", "fraud_likelihood: 0.2, "fraud_reason": "Multiple transactions from different countries"}

                """)
        // .includeContents(IncludeContents.NONE)
        .tools(List.of(publishRecord))
        .build();
    return fraudAgent;

  }



  public static Map<String, Object> publishRecord(@Schema(name = "topic", description = "The topicto which to publish") String topic, @Schema(name = "json", description = "The json to publish") String json) {
    System.out.println("PUBLISH " + topic + " " + json);

    Publisher publisher = publishers.get(topic);
    if (publisher == null) {
      try {
        TopicName topicName = TopicName.parse(topic);
        publisher = Publisher.newBuilder(topicName).build();
      } catch (Exception e) {
        System.err.println("Could not create publisher: " + e);
        return Map.of();
      }
      publishers.put(topic, publisher);
    }
    ByteString data = ByteString.copyFromUtf8(json);
    PubsubMessage pubsubMessage = PubsubMessage.newBuilder().setData(data).build();
    try {
      publisher.publish(pubsubMessage).get();
    } catch (Exception e) {
        System.err.println("Could not publish: " + e);
        return Map.of();
    }
    System.out.println("Successfully published!");
    return Map.of();
  }
}