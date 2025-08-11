package by.northdakota.markettracker.Core.Parser.WB;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WbParser {

    public String getProductName(JSONObject productData){
        JSONArray productArr = productData.getJSONArray("products");
        Object productName = productArr.getJSONObject(0).toMap().get("name");
        return productName.toString();
    }

    public Map<String,Object> getPriceList(JSONObject productData){
        JSONArray json = productData.getJSONArray("products");
        JSONObject product = json.getJSONObject(0);
        JSONArray sizes = product.getJSONArray("sizes");
        JSONObject size = sizes.getJSONObject(0);
        JSONObject price = size.getJSONObject("price");
        return price.toMap();
    }


}
