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
import logging
import os
import tomllib as tomllib


# TODO: Fill in these values for your project
PROJECT_ID = ""
LOCATION = "us-central1"  # For other options, see https://cloud.google.com/vertex-ai/generative-ai/docs/agent-engine/overview#supported-regions
STAGING_BUCKET = ""

logging.basicConfig(level=logging.DEBUG)
logger = logging.getLogger(__name__)

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
        Gets a session by its ID, creating it if it does not exist.

        This method is guaranteed to return a Session object and will not
        return None.
        """
        logger.info("Get session " + user_id + " " + session_id)
        # Attempt to get the session from the proxied service
        session = await self._proxied_service.get_session(
            app_name=app_name,
            user_id=user_id,
            session_id=session_id,
            config=config,
        )

        # If it doesn't exist (returns None), create it implicitly.
        if session is None:
            session = await self._proxied_service.create_session(
                app_name=app_name,
                user_id=user_id,
                session_id=session_id,
                # The implicitly created session starts with an empty state.
                state=None,
            )
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
  vaiss = VertexAiSessionService(
      project=PROJECT_ID,
      location=LOCATION,
      agent_engine_id=os.environ.get("GOOGLE_CLOUD_AGENT_ENGINE_ID"),
  )
  inss = InMemorySessionService()
  return ImplicitSessionService(inss)

# Initialize the Vertex AI SDK
vertexai.init(
    project=PROJECT_ID,
    location=LOCATION,
    staging_bucket=STAGING_BUCKET,
)
# Wrap the agent in an AdkApp object
logger.info("Start it up")
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
# async def main():
#     """The main async function to run our code."""
#     print("Entered the main async function.")
#     events = []
#     async for event in app.async_stream_query(
#         user_id="u_123",
#         session_id="12345",
#         message="{\"credit_card_number\": \"1234567812345678\", \"receiver\": \"Best Buy\", \"amount\": 10000.05, \"ip_address\": \"103.109.106.5\"}",
#     ):
#         events.append(event)

#     # The full event stream shows the agent's thought process
#     print("--- Full Event Stream ---")
#     for event in events:
#         print(event)
# asyncio.run(main())
