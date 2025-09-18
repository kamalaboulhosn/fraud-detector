package server;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.events.Event;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.sessions.Session;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.adk.sessions.GetSessionConfig;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.reactivex.rxjava3.core.Maybe;
import java.util.Optional;

import agents.fraudagent.FraudAgent;
import io.reactivex.rxjava3.core.Flowable;

public class AgentCaller {
  private static final String APP_NAME = "FraudDetector";

  private InMemoryRunner runner;

  public AgentCaller() {
    runner = new InMemoryRunner(FraudAgent.ROOT_AGENT, APP_NAME);
  }

  public String executeRequest(String message) {
    JsonObject jsonObject = JsonParser.parseString(message).getAsJsonObject();
    String ccNumber = jsonObject.get("credit_card_number").getAsString();
    BaseSessionService sessionService = runner.sessionService();
    Session session = sessionService.getSession(APP_NAME, ccNumber, ccNumber, Optional.empty()).blockingGet();
    if (session == null) {
      session = sessionService.createSession(APP_NAME, ccNumber, null, ccNumber).blockingGet();
    }
    Content userMessage = Content.fromParts(Part.fromText(message));
    Flowable<Event> eventStream = runner.runAsync(ccNumber, session.id(), userMessage);
    Event event = eventStream.blockingLast();
    return event.stringifyContent();
  }
}
