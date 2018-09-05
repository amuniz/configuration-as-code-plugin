package io.jenkins.plugins.casc.impl.configurators;

import com.cloudbees.plugins.credentials.api.resource.APIExportable;
import com.cloudbees.plugins.credentials.api.resource.APIResource;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import hudson.Extension;
import io.jenkins.plugins.casc.BaseConfigurator;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorException;
import io.jenkins.plugins.casc.model.CNode;
import io.jenkins.plugins.casc.model.Mapping;
import io.jenkins.plugins.casc.model.Scalar;
import io.jenkins.plugins.casc.yaml.ModelConstructor;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Represent;
import org.yaml.snakeyaml.representer.Representer;

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

        // converting back to yaml to map it later to the resource object
        // NOTE: would not be needed if the mapping object had a reference to the YAML fragment that produced it
        Yaml yaml = new Yaml(new ScalarRepresenter());
        String dump = yaml.dump(mapping);

        try {
            // this assumes the type returned by getDataAPI is the actual resource leaf
            Method getDataAPI = target.getDeclaredMethod("getDataAPI");
            Class<?> resource = getDataAPI.getReturnType();

            // Using Jackson as the Data API is using its annotations
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            Object resourceInstance = mapper.readValue(dump, resource);

            // return the model instance constructed from the resource
            return (T) ((APIResource) resourceInstance).toModel();

        } catch (NoSuchMethodException | IOException e) {
            throw new ConfiguratorException(e);
        }
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

    // internal, used to serialize the Mapping object
    private class ScalarRepresenter extends Representer {

        private class ScalarRepresent implements Represent {

            @Override
            public Node representData(Object data) {
                Scalar scalar = (Scalar) data;
                Tag tag = null;
                if (Scalar.Format.STRING.equals(scalar.getFormat())) {
                    tag = Tag.STR;
                } else if (Scalar.Format.NUMBER.equals(scalar.getFormat())) {
                    tag = Tag.INT;
                } else if (Scalar.Format.BOOLEAN.equals(scalar.getFormat())) {
                    tag = Tag.BOOL;
                }
                return representScalar(tag, ((Scalar) data).getValue());
            }
        }

        ScalarRepresenter() {
            this.representers.put(Scalar.class, new ScalarRepresent());
        }
    }
}
