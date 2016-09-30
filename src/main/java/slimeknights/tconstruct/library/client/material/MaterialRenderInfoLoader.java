package slimeknights.tconstruct.library.client.material;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import slimeknights.tconstruct.library.TinkerRegistry;
import slimeknights.tconstruct.library.Util;
import slimeknights.tconstruct.library.client.material.deserializers.ColoredRenderInfoDeserializer;
import slimeknights.tconstruct.library.client.model.ModelHelper;
import slimeknights.tconstruct.library.materials.Material;

public class MaterialRenderInfoLoader implements IResourceManagerReloadListener {

  public static final MaterialRenderInfoLoader INSTANCE = new MaterialRenderInfoLoader();

  private static Logger log = Util.getLogger("RenderInfoLoader");

  private static final Type TYPE = new TypeToken<IMaterialRenderInfoDeserializer>() {}.getType();
  private static final Gson GSON = new GsonBuilder()
      .registerTypeAdapter(TYPE, new MaterialInfoDeserializerDeserializer())
      .create();

  private IResourceManager resourceManager;

  static Map<String, Class<? extends IMaterialRenderInfoDeserializer>> renderInfoDeserializers = Maps.newHashMap();

  public static void addRenderInfo(String id, Class<? extends IMaterialRenderInfoDeserializer> clazz) {
    renderInfoDeserializers.put(id, clazz);
  }

  // we load from mods resource locations, in this order:
  // <mod that registered the material> -> tconstruct -> minecraft
  public void loadRenderInfo() {
    for(Material material : TinkerRegistry.getAllMaterials()) {
      // check if info exists in the form of json
      // if not, check if there already is data
      // if no data exists and no json is present, fill it with textcolor default

      List<String> domains = Lists.newArrayList();

      if(!Util.MODID.equals(TinkerRegistry.getTrace(material))) {
        domains.add(TinkerRegistry.getTrace(material));
      }

      domains.add(Util.MODID);
      domains.add("minecraft");

      for(String domain : domains) {
        try {
          ResourceLocation location = new ResourceLocation(domain, "materials/" + material.getIdentifier());
          Reader reader = ModelHelper.getReaderForResource(location, resourceManager);
          IMaterialRenderInfoDeserializer deserializer = GSON.fromJson(reader, TYPE);
          if(deserializer != null) {
            material.renderInfo = deserializer.getMaterialRenderInfo();
          }
        } catch(IOException e) {
          log.error("Exception when loading render info for material " + material.getIdentifier(), e);
        }
      }
    }
  }

  @Override
  public void onResourceManagerReload(IResourceManager resourceManager) {
    this.resourceManager = resourceManager;
    loadRenderInfo();
  }

  private static class MaterialInfoDeserializerDeserializer implements JsonDeserializer<IMaterialRenderInfoDeserializer> {

    @Override
    public IMaterialRenderInfoDeserializer deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
      JsonObject jsonObject = json.getAsJsonObject();
      String type = jsonObject.get("type").getAsString();

      Class<? extends IMaterialRenderInfoDeserializer> deserializerClass = renderInfoDeserializers.get(type);

      if(deserializerClass == null) {
        throw new JsonParseException("Unknown material texture type: " + type);
      }

      JsonElement parameters = jsonObject.get("parameters");
      return GSON.fromJson(parameters, deserializerClass);
    }
  }

}
