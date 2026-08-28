# Web Agent

Jarvis can perform real web research via backend-proxied tools.

## Supported Tools
- `web_search(query)`: Sends a request to the backend `/api/v1/web/search` to retrieve search results.
- `web_open(url)`: Sends a request to the backend `/api/v1/web/open`, which fetches the URL, parses the HTML via Cheerio, and returns the cleaned text content to the agent.

These tools allow Jarvis to read actual articles, documentation, and news without exposing direct web scraping complexities to the Android client.
