package fr.itldev.koya.model.json;

import fr.itldev.koya.model.impl.NotificationDetails;
import java.io.IOException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.annotate.JsonMethod;
import org.codehaus.jackson.map.DeserializationContext;
import org.codehaus.jackson.map.JsonDeserializer;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.annotate.JsonDeserialize;


public class NotificationDetailsDeserializer extends JsonDeserializer<NotificationDetails>{

    @Override
    public NotificationDetails deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper().setVisibility(JsonMethod.FIELD, Visibility.ANY);

        String notificationDetailsStr = jp.getText();
        return mapper.readValue(notificationDetailsStr, NotificationDetails.class);
    }
    
}