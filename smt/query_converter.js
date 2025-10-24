/**
 * Transforms a raw transaction event into a structured query format, using the
 * credit card number as the user_id and session_id. Throws an error if the
 * message data is invalid.
 *
 * @param {Object} message The Pub/Sub message. The message.data is expected
 * to be a JSON string with a 'credit_card_number' field.
 * @param {Object} metadata The Pub/Sub message metadata.
 * @return {Object} The transformed message.
 * @throws {Error} If message data is not valid JSON or is missing the
 * 'credit_card_number' field.
 */
function transformTransaction(message, metadata) {
  // JSON.parse will throw an error if message.data is not a valid JSON string.
  const inputJson = JSON.parse(message.data);
  const creditCardNumber = inputJson.credit_card_number;
  // Throw an error if the required field is missing.
  if (!creditCardNumber) {
    throw new Error("Field 'credit_card_number' not found in message data.");
  }

  // Construct the new data payload.
  const outputData = {
    class_method: "async_stream_query",
    input: {
      user_id: creditCardNumber,
      message: message.data, // The original data string
    },
  };
  // Return the new message with transformed data and original attributes.
  return {
    data: JSON.stringify(outputData),
    attributes: message.attributes,
  };
}