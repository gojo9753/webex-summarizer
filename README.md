# WebEx Conversation Downloader & Summarizer

A Java application for downloading conversations from Cisco WebEx rooms and generating summaries using AWS Bedrock's Large Language Models (LLMs).

## Features

- **WebEx Authentication**: Simple token-based authentication with the Cisco WebEx API
- **Room Management**: List, filter, and access details of WebEx rooms
- **Message Management**: Download, display, and organize messages from WebEx rooms
- **Enhanced Pagination Support**: Automatically handles paginated API responses to retrieve all messages, even for large rooms with thousands of messages
- **Local Storage**: Store downloaded conversations in structured JSON format
- **Conversation Summarization**: Generate concise summaries of conversations using AWS Bedrock's LLMs, with support for processing large conversations through intelligent chunking
- **Command-line Interface**: Simple CLI for all operations with dedicated subcommands
- **AWS Integration**: Uses AWS SDK and credentials for secure access to Bedrock models
- **Modern LLM Support**: Compatible with the latest Claude models including us.anthropic.claude-sonnet-4-20250514-v1:0 using the newer Messages API

## Requirements

- Java 11 or newer
- Maven 3.6 or newer
- A Cisco WebEx account with API access
- An AWS account with access to AWS Bedrock
- AWS credentials configured (via AWS CLI or credentials file)

## Installation

1. Clone this repository:
   ```
   git clone https://github.com/gojo9753/webex-summarizer.git
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
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar summarize --room ROOM_ID
```

Filter messages by date range (from specific date to today):

```
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar summarize --room ROOM_ID --start-date 2023-01-01
```

You can use the shorter alias `--from`:

```
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar summarize --room ROOM_ID --from 2023-01-01
```

Or specify both start and end dates:

```
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar summarize --room ROOM_ID --start-date 2023-01-01 --end-date 2023-01-31
```

You can also summarize a downloaded conversation file with date filtering:

```
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar summarize --file "conversations/filename.json" --start-date 2023-01-01
```

You can use alternative shorter parameter names:

```
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar summarize --file "conversations/filename.json" --from 2023-01-01 --to 2023-01-31
```

Filter only messages from a specific date to current date:

```
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar summarize --file "conversations/filename.json" --from 2023-01-01
```

Note: Always enclose filenames in quotes, especially if they contain spaces or special characters.

You can specify AWS options:
```
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar summarize --room ROOM_ID --aws-profile custom-profile --region us-west-2 --model anthropic.claude-3-sonnet-20240229-v1:0
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

With pagination and message references:

```
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar --read path/to/conversation/file.json --limit 50 --page 1 --references
```

Options:
- `--limit <number>`: Set the number of messages to display per page (default: 1000)
- `--page <number>`: Select which page of messages to display (default: 1)
- `--references`: Show message reference numbers and IDs (default: true)
- `--no-references`: Hide message reference numbers and IDs

### Summarize an Existing Conversation

Generate a summary for a previously downloaded conversation:

```
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar -s path/to/conversation/file.json
```

For large conversations, the app will automatically split the conversation into manageable chunks, summarize each chunk, and then combine these summaries into a cohesive final summary. Progress indicators will show you the status of this multi-stage process.

### New Subcommands for Room and Message Management

#### List Rooms with Enhanced Options

Use the dedicated room listing subcommand:

```
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar list-rooms
```

Filter rooms by type:

```
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar list-rooms --type group
```

View details for a specific room:

```
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar list-rooms --id ROOM_ID
```

#### List Messages from a Room or File

List messages from a WebEx room:

```
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar list-messages --room ROOM_ID
```

List messages from a previously saved conversation file:

```
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar list-messages --file path/to/conversation/file.json
```

With pagination and message references:

```
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar list-messages --room ROOM_ID --limit 50 --page 2 --references
```

Save the downloaded messages to a file:

```
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar list-messages --room ROOM_ID --save
```

Advanced Options:
- `--limit <number>`: Set the number of messages to display per page (default: 1000)
- `--page <number>`: Select which page of messages to display (default: 1)
- `--references`: Show message reference numbers and IDs (default: true)
- `--no-references`: Hide message reference numbers and IDs

#### Manage Summaries

Generate a summary from a WebEx room:

```
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar summarize --room ROOM_ID
```

Filter messages by date range:

```
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar summarize --room ROOM_ID --start-date 2023-01-01 --end-date 2023-01-31
```

Summarize a previously downloaded conversation:

```
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar summarize --file path/to/conversation/file.json
```

List all conversations with summaries:

```
java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar summarize --list-summaries
```

The summarize command automatically handles large conversations by:

1. Intelligently analyzing message content and estimating token usage
2. Splitting conversations into optimally-sized chunks based on token counts
3. Processing each chunk individually while monitoring token limits
4. Combining all chunk summaries into a coherent final summary
5. Displaying progress with a visual progress bar

This ensures that even conversations with thousands of messages or very large individual messages can be summarized effectively while staying within LLM token limits. The system automatically adjusts chunking based on actual message content instead of just message counts.

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

## Technical Details

### WebEx API Integration
- Uses the WebEx Messages API with 'beforeMessage' parameter for efficient pagination
- Reliably handles rooms with thousands of messages
- Compares returned message counts with requested counts to detect end of message history

### Message References
- All messages have unique reference numbers (#0001, #0002, etc.)
- Original WebEx message IDs are preserved and displayed
- Reference system works across pagination boundaries

### Pagination
- Configurable page size (--limit parameter)
- Navigation between pages (--page parameter)
- Clear display of current page position and total pages

### AWS Bedrock Integration
- Intelligent model detection to use the appropriate API for each model type
- Support for legacy API (Claude v2, Titan, Llama) and newer Messages API (Claude 3+)
- Compatible with the latest Claude models including us.anthropic.claude-sonnet-4-20250514-v1:0
- Enhanced summarization quality with modern models

### Token-based Chunking
- Intelligently estimates token usage for each message
- Splits conversations based on actual content size rather than message count
- Ensures that each chunk stays below model context limits (15,000 tokens by default)
- Handles large individual messages by placing them in their own chunks
- Dynamically adjusts chunking strategy based on conversation characteristics

## License

This project is licensed under the MIT License - see the LICENSE file for details.
