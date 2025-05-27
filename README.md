# WebEx Conversation Downloader & Summarizer

A Java application for downloading conversations from Cisco WebEx rooms and generating summaries using AWS Bedrock's Large Language Models (LLMs).

## Features

- **WebEx Authentication**: Simple token-based authentication with the Cisco WebEx API
- **Room Selection**: Download conversations from specific WebEx rooms
- **Pagination Support**: Automatically handles paginated API responses to retrieve all messages
- **Local Storage**: Store downloaded conversations in structured JSON format
- **Conversation Summarization**: Generate concise summaries of conversations using AWS Bedrock's LLMs
- **Command-line Interface**: Simple CLI for all operations
- **AWS Integration**: Uses AWS SDK and credentials for secure access to Bedrock models

## Requirements

- Java 11 or newer
- Maven 3.6 or newer
- A Cisco WebEx account with API access
- An AWS account with access to AWS Bedrock
- AWS credentials configured (via AWS CLI or credentials file)

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

3. Ensure you have configured AWS credentials (either through AWS CLI or by creating a `~/.aws/credentials` file)

4. Run the application to generate a default configuration file:
   ```
   java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar
   ```

5. Edit the generated `config.properties` file with your WebEx token and AWS settings.

## Configuration

Edit the `config.properties` file with the following information:

```properties
# WebEx Configuration
webex.token=YOUR_WEBEX_TOKEN
storage.directory=conversations

# AWS Bedrock Configuration
aws.profile=rivendel
aws.region=us-east-1
aws.bedrock.model=anthropic.claude-v2
```

- You can obtain a WebEx token from the [Cisco WebEx Developer Portal](https://developer.webex.com/)
- The AWS profile "rivendel" will be used by default, but can be overridden with command-line options
- AWS region defaults to us-east-1 but can be changed
- Default model is Claude v2 from Anthropic, but you can choose other models with the list-models command

## Usage

### Set Your WebEx Token

There are two ways to set your WebEx token:

1. Enter it interactively:
   ```
   java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar --auth
   ```
   When prompted, enter your WebEx token.

2. Provide it directly as a command-line argument:
   ```
   java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar --token YOUR_WEBEX_TOKEN
   ```

### List Available Rooms

List the WebEx rooms you have access to:

```
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar --list-rooms
```

### Download a Conversation

Download messages from a specific room (replace ROOM_ID with the ID from the list-rooms command):

```
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar -r ROOM_ID
```

### Generate a Summary

Download a conversation and generate a summary in one command:

```
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar -r ROOM_ID -s
```

You can specify AWS options:
```
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar -r ROOM_ID -s -p custom-profile --region us-west-2 -m anthropic.claude-3-sonnet-20240229-v1:0
```

### List Downloaded Files

List previously downloaded conversation files:

```
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar --list-files
```

### Read an Existing Conversation

Read a previously downloaded conversation without summarizing:

```
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar --read path/to/conversation/file.json
```

### Summarize an Existing Conversation

Generate a summary for a previously downloaded conversation:

```
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar -s path/to/conversation/file.json
```

### List Available Bedrock Models

View the available AWS Bedrock models:

```
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar list-models
```

To get details about a specific model:

```
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar list-models --detail MODEL_ID
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
- The application uses direct token authentication with WebEx to access room data.

## Development

To contribute to the project:

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.