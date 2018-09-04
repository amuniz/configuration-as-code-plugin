package io.jenkins.plugins.casc.impl.configurators;

import com.cloudbees.plugins.credentials.api.resource.APIExportable;
import com.cloudbees.plugins.credentials.api.resource.APIResource;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import hudson.Extension;
import io.jenkins.plugins.casc.BaseConfigurator;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorException;
import io.jenkins.plugins.casc.model.CNode;
import io.jenkins.plugins.casc.model.Mapping;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.MappingNode;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.lang.reflect.Method;

public class APIResourceConfigurator<T> extends BaseConfigurator<T> {

    private final Class<T> target;

    public APIResourceConfigurator(Class<T> target) {
        this.target = target;
    }

    @Override
    protected T instance(Mapping mapping, ConfigurationContext context) throws ConfiguratorException {
        Yaml yaml = new Yaml();
        String dump = yaml.dump(mapping);
        try {
            Method getDataAPI = target.getDeclaredMethod("getDataAPI");
            Class<?> resource = getDataAPI.getReturnType();
            // resources have a constructor without parameters

            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            Object resourceInstance = mapper.readValue(dump, resource);

            return (T) ((APIResource) resourceInstance).toModel();

        } catch (NoSuchMethodException e) {
            // should not happen
            e.printStackTrace();
        } catch (JsonParseException e) {
            e.printStackTrace();
        } catch (JsonMappingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Class<T> getTarget() {
        return target;
    }

    @CheckForNull
    @Override
    public CNode describe(T instance, ConfigurationContext context) throws Exception {
        // get the APIResource from the instance and return the description of it (by introspection)
        // in an APIResource all public fields have a getter, search for that
        return null;
    }

    /**
     * Can handle the class if it is the target and it implements the APIExportable interface.
     */
    @Override
    public boolean canConfigure(Class clazz) {
        return clazz == getTarget() && isImplementingDataAPI(clazz);
    }

    private boolean isImplementingDataAPI(Class clazz) {
        // the model is implementing the data-api
        return APIExportable.class.isAssignableFrom(clazz);
    }
}
