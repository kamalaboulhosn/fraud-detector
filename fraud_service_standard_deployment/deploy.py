import vertexai
from google.adk.sessions import BaseSessionService
from google.adk.sessions import InMemorySessionService
from google.adk.sessions.vertex_ai_session_service import (
    VertexAiSessionService,
)
from agent.agent import root_agent # modify this if your agent is not in agent.py
from vertexai import agent_engines
from typing import Any
from typing import Optional
from typing_extensions import override
import asyncio
import os
import tomllib as tomllib


# TODO: Fill in these values for your project
PROJECT_ID = ""
LOCATION = "us-central1"  # For other options, see https://cloud.google.com/vertex-ai/generative-ai/docs/agent-engine/overview#supported-regions
STAGING_BUCKET = ""

session_service = None

class ImplicitSessionService(BaseSessionService):
    """
    A session service that proxies another BaseSessionService and ensures
    that get_session always returns a session, creating one if it doesn't exist.
    """
    def __init__(self, proxied_service: BaseSessionService):
        """
        Initializes the service with another session service to proxy to.

        Args:
            proxied_service: An instance of a class that implements
                             BaseSessionService.
        """
        if not isinstance(proxied_service, BaseSessionService):
            raise TypeError("proxied_service must be an instance of BaseSessionService")
        self._proxied_service = proxied_service

    @override
    async def get_session(
        self,
        *,
        app_name: str,
        user_id: str,
        session_id: str,
        config = None,
    ):
        """
        Gets a session for a user, creating it if it does not exist. Assume
        one session per user exists.

        This method is guaranteed to return a Session object and will not
        return None.
        """
        print("Get session " + user_id + " " + session_id)
        sessions = await self._proxied_service.list_sessions(
            app_name=app_name,
            user_id=user_id,
        )
        if len(sessions.sessions) > 0:
          print("Found an existing session " + str(len(sessions.sessions)))
          session = sessions.sessions[0]
        else:
          print("Creating a session")
          session = await self._proxied_service.create_session(
              app_name=app_name,
              user_id=user_id,
              #session_id=session_id,
              # The implicitly created session starts with an empty state.
              state=None,
          )
        print("Got session " + user_id)
        print(session)
        return session

    @override
    async def create_session(
        self,
        *,
        app_name: str,
        user_id: str,
        state: Optional[dict[str, Any]] = None,
        session_id: Optional[str] = None,
    ):
        print("Creating a required session!")
        """Proxies the create_session call to the underlying service."""
        return await self._proxied_service.create_session(
            app_name=app_name,
            user_id=user_id,
            state=state,
            session_id=session_id,
        )

    @override
    async def list_sessions(
        self, *, app_name: str, user_id: str
    ):
        """Proxies the list_sessions call to the underlying service."""
        return await self._proxied_service.list_sessions(
            app_name=app_name, user_id=user_id
        )

    @override
    async def delete_session(
        self, *, app_name: str, user_id: str, session_id: str
    ) -> None:
        """Proxies the delete_session call to the underlying service."""
        await self._proxied_service.delete_session(
            app_name=app_name, user_id=user_id, session_id=session_id
        )

def get_session_service():
  global session_service
  if session_service is None:
    vaiss = VertexAiSessionService(
        project=PROJECT_ID,
        location=LOCATION,
    )
    session_service = ImplicitSessionService(vaiss)
  return session_service

# Initialize the Vertex AI SDK
vertexai.init(
    project=PROJECT_ID,
    location=LOCATION,
    staging_bucket=STAGING_BUCKET,
)
# Wrap the agent in an AdkApp object
print("Start it up")
app = agent_engines.AdkApp(
    agent=root_agent,
    enable_tracing=True,
    session_service_builder=get_session_service
)

def load_requirements():
    """Loads requirements from pyproject.toml."""
    pyproject_path = os.path.abspath(
        os.path.join(os.path.dirname(__file__), "pyproject.toml")
    )
    with open(pyproject_path, "rb") as f:
        pyproject_data = tomllib.load(f)
    return pyproject_data["project"]["dependencies"]

remote_app = agent_engines.create(
    app,
    display_name="Fraund Agent",
    requirements=load_requirements(),
    extra_packages=[
        "./agent",
    ],
)

# Uncomment below  and comment out remote_app definition to test locally.
# async def make_request(amount, ip, timestamp):
#     events = []
#     async for event in app.async_stream_query(
#         user_id="8234567812345634",
#         session_id="8234567812345634",
#         message="{\"credit_card_number\": \"8234567812345634\", \"receiver\": \"My Shop\", \"amount\": " + str(amount) + "\", ip_address\": \"" + ip + "\", \"timestamp\":" + timestamp + "\"}"
#     ):
#         events.append(event)

#     # The full event stream shows the agent's thought process
#     print("--- Full Event Stream ---")
#     for event in events:
#         print("EVENT:" + str(event))

# async def main():
#     """The main async function to run our code."""
#     print("Entered the main async function.")
#     await make_request(10.05, "103.109.106.5", "2025-10-01 07:00:00.00000Z")
#     await make_request(12000.99, "63.35.253.23", "2025-10-01 07:10:00.00000Z")
# asyncio.run(main())
