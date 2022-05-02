package com.lunar.message;

import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.TemplateType;

public class CommandTest {
//	public static class CommandTypeAdapter implements JsonSerializer<Command>, JsonDeserializer<Command>{
//		Gson gson = new Gson();
//		public JsonElement serialize(Command command, Type typeOfT, JsonSerializationContext context)
//		{
//			JsonObject json = new JsonObject();
//			json.addProperty("commandType", command.commandType().name());
//			json.addProperty("senderSinkId", command.senderSinkId());
//			json.addProperty("toSend", command.toSend().name());
//
//			final JsonArray jsonArray = new JsonArray();
//			for (Parameter parameter : command.parameters())
//			{
//				jsonArray.
//				json.addProperty(user.getName(), gson.toJson(parameter));
//			}
//
//			return json;
//		}
//
//		public Command deserialize(JsonElement element, Type typeOfT, JsonDeserializationContext context) throws JsonParseException
//		{
//			JsonObject json = element.getAsJsonObject();
//
//			Book book = new Book();
//
//			for (Entry<String, JsonElement> entry : json.entrySet())
//			{
//				String name = entry.getKey();
//				User user = gson.fromJson(entry.getValue(), User.class);
//				user.setName(name);
//
//				book.getUser().add(user); 
//			}
//
//			return book;
//		}
//	}

	private static final Logger LOG = LogManager.getLogger(CommandTest.class);
	@Test
	public void testGson(){
		Command expectedCommand = Command.of(1, 2, CommandType.START);
		Gson gson = new Gson();
		String json = gson.toJson(expectedCommand);
		LOG.info("Command {}", json);

//		Command actualCommand = gson.fromJson(json, Command.class);
//		assertEquals(expectedCommand, actualCommand);
		
		Command command2 = Command.of(1, 
				2,
				CommandType.START, 
				new ImmutableList.Builder<Parameter>().add(
				Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.EXCHANGE.value()),
				Parameter.of(ParameterType.EXCHANGE_CODE, "SEHK")).build());
		json = gson.toJson(command2);
		LOG.info("Command2 {}", json);

	}	
}
