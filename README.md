# WebEx Conversation Downloader & Summarizer

A Java application for downloading conversations from Cisco WebEx rooms and generating summaries using Large Language Models (LLMs).

## Features

- **WebEx Authentication**: Securely authenticate with the Cisco WebEx API using OAuth 2.0
- **Room Selection**: Download conversations from specific WebEx rooms
- **Pagination Support**: Automatically handles paginated API responses to retrieve all messages
- **Local Storage**: Store downloaded conversations in structured JSON format
- **Conversation Summarization**: Generate concise summaries of conversations using configurable LLMs
- **Command-line Interface**: Simple CLI for all operations

## Requirements

- Java 11 or newer
- Maven 3.6 or newer
- A Cisco WebEx account with API access
- API credentials for your preferred LLM service (e.g., OpenAI)

## Installation

1. Clone this repository:
   ```
   git clone https://github.com/your-username/webex-summarizer.git
   cd webex-summarizer
   ```

2. Build the application:
   ```
   mvn clean package
   ```

3. Run the application to generate a default configuration file:
   ```
   java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar
   ```

4. Edit the generated `config.properties` file with your WebEx API credentials and LLM API details.

## Configuration

Edit the `config.properties` file with the following information:

```properties
webex.client.id=YOUR_WEBEX_CLIENT_ID
webex.client.secret=YOUR_WEBEX_CLIENT_SECRET
webex.redirect.uri=http://localhost:8080/callback
storage.directory=conversations
llm.api.endpoint=https://api.openai.com/v1/completions
llm.api.key=YOUR_LLM_API_KEY
```

You can obtain WebEx API credentials from the [Cisco WebEx Developer Portal](https://developer.webex.com/).

## Usage

### Authentication

First, authenticate with the WebEx API to obtain an access token:

```
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar --auth
```

Follow the prompts to complete the OAuth authentication flow.

### List Available Rooms

List the WebEx rooms you have access to:

```
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar --list-rooms
```

### Download a Conversation

Download messages from a specific room (replace ROOM_ID with the ID from the list-rooms command):

```
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar --room ROOM_ID
```

### Generate a Summary

Download a conversation and generate a summary in one command:

```
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar --room ROOM_ID --summarize
```

### List Downloaded Files

List previously downloaded conversation files:

```
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar --list-files
```

### Summarize an Existing Conversation

Generate a summary for a previously downloaded conversation:

```
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar --summarize path/to/conversation/file.json
```

## Output Format

Downloaded conversations are stored in JSON format with the following naming convention:
```
RoomName_RoomID_Timestamp.json
```

The JSON structure includes:
- Room details (ID, title, type)
- All messages (sender, timestamp, content)
- Summary (if generated)

## Security Notes

- API tokens and keys are stored in the local configuration file. Ensure this file has appropriate permissions.
- The application requires OAuth 2.0 authentication with WebEx to access room data.

## Development

To contribute to the project:

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.