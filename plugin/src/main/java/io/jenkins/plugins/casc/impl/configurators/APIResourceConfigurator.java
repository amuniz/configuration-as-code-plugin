package io.jenkins.plugins.casc.impl.configurators;

import com.cloudbees.plugins.credentials.api.resource.APIExportable;
import com.cloudbees.plugins.credentials.api.resource.APIResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.jenkins.plugins.casc.BaseConfigurator;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorException;
import io.jenkins.plugins.casc.SecretSource;
import io.jenkins.plugins.casc.model.CNode;
import io.jenkins.plugins.casc.model.Mapping;
import io.jenkins.plugins.casc.model.Scalar;
import io.jenkins.plugins.casc.yaml.YamlSource;
import io.jenkins.plugins.casc.yaml.YamlUtils;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Represent;
import org.yaml.snakeyaml.representer.Representer;

import javax.annotation.CheckForNull;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

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
    protected void configure(Mapping config, T instance, boolean dryrun, ConfigurationContext context) throws ConfiguratorException {
        // no-op, already configured before
    }

    @Override
    public Class<T> getTarget() {
        return target;
    }

    @CheckForNull
    @Override
    public CNode describe(T instance, ConfigurationContext context) throws Exception {
        // get the Data layer resource
        APIResource data = ((APIExportable) instance).getDataAPI();

        // Serialize it as yaml (needed snakeyaml upgrade: https://github.com/FasterXML/jackson-dataformats-text/issues/81)
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        String yaml = mapper.writeValueAsString(data);

        // re-parse it to Mappings
        return YamlUtils.loadFrom(Arrays.asList(new YamlSource(new ByteArrayInputStream(yaml.getBytes()), YamlSource.READ_FROM_INPUTSTREAM)));
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
                return representScalar(tag, resolveSecret(((Scalar) data).getValue()));
            }

            private String resolveSecret(String value) {
                String s = value;
                Optional<String> r = SecretSource.requiresReveal(value);
                if (r.isPresent()) {
                    final String expr = r.get();
                    Optional<String> reveal = Optional.empty();
                    for (SecretSource secretSource : SecretSource.all()) {
                        try {
                            reveal = secretSource.reveal(expr);
                        } catch (IOException ex) {
                            throw new RuntimeException("Cannot reveal secret source for variable with key: " + s, ex);
                        }
                        if (reveal.isPresent()) {
                            s = reveal.get();
                            break;
                        }
                    }

                    Optional<String> defaultValue = SecretSource.defaultValue(value);
                    if (defaultValue.isPresent() && !reveal.isPresent()) {
                        s = defaultValue.get();
                    }

                    if (!reveal.isPresent() && !defaultValue.isPresent()) {
                        throw new RuntimeException("Unable to reveal variable with key: " + s);
                    }
                }
                return s;
            }
        }

        ScalarRepresenter() {
            this.representers.put(Scalar.class, new ScalarRepresent());
        }
    }
}
