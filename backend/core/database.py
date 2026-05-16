import os
import chromadb
from chromadb.utils import embedding_functions
from chromadb.api.types import Documents, Embeddings
from neo4j import GraphDatabase
import firebase_admin
from firebase_admin import credentials
from .config import settings
from .ai import client

class GeminiEmbeddingFunction(embedding_functions.EmbeddingFunction):
    def __call__(self, input: Documents) -> Embeddings:
        res = client.models.embed_content(model="gemini-embedding-001", contents=input)
        return [e.values for e in res.embeddings]
    def name(self) -> str: return "google_generative_ai"

# ChromaDB
chroma_client = chromadb.PersistentClient(path="./chroma_db")
collection = chroma_client.get_or_create_collection(
    name="user_memories", 
    embedding_function=GeminiEmbeddingFunction()
)

# Neo4j
neo4j_driver = GraphDatabase.driver(
    settings.NEO4J_URI,
    auth=(settings.NEO4J_USER, settings.NEO4J_PASSWORD)
)

# Firebase
if not firebase_admin._apps:
    if os.path.exists("serviceAccountKey.json"):
        firebase_admin.initialize_app(credentials.Certificate("serviceAccountKey.json"))
