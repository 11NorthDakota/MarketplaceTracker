package by.northdakota.markettracker.Core.Parser;

import by.northdakota.markettracker.Core.Parser.WB.WbParser;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WbParserTest {

    private static final WbParser wbParser = new WbParser();

    @Test
    void getProductNameTest() throws IOException {
        JSONObject data = new JSONObject("""
                {
                 "products":[
                    {
                        "name":"футболка-поло"
                    }
                 ]
                }
                """);
        String productName = wbParser.getProductName(data);
        assertEquals(productName,"футболка-поло");
    }

    @Test
    void getPriceList(){
        JSONObject data = new JSONObject("""
                {
                 "products":[
                    {
                        "sizes":[
                            {
                                "price":{
                                    "basic": 361600,
                                    "product": 116100,
                                    "logistics": 3000,
                                    "return": 0
                                }
                            }
                        ]
                    }
                 ]
                }
                """);
        Map<String, Object> price = wbParser.getPriceList(data);
        Map<String, Object> expected = Map.of(
                "basic", 361600,
                "product", 116100,
                "logistics", 3000,
                "return", 0
        );
        assertEquals(expected, price);
    }

}