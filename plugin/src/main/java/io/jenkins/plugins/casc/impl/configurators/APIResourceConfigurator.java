package io.jenkins.plugins.casc.impl.configurators;

import com.cloudbees.plugins.credentials.api.resource.APIExportable;
import io.jenkins.plugins.casc.BaseConfigurator;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorException;
import io.jenkins.plugins.casc.model.CNode;
import io.jenkins.plugins.casc.model.Mapping;

import javax.annotation.CheckForNull;

public class APIResourceConfigurator<T> extends BaseConfigurator<T> {

    private final Class<T> target;

    public APIResourceConfigurator(Class<T> target) {
        this.target = target;
    }

    @Override
    protected T instance(Mapping mapping, ConfigurationContext context) throws ConfiguratorException {
        // create an APIResource based on mapping and return the model for it
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

    @Override
    public boolean canConfigure(Class clazz) {
        return clazz == getTarget() && isImplmentingDataAPI(clazz);
    }

    private boolean isImplmentingDataAPI(Class clazz) {
        // the model is implementing the data-api
        return APIExportable.class.isAssignableFrom(clazz);
    }
}
