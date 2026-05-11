package p4_aas.Submodels;

import p4_aas.Submodels.Utils.Utils;

/**
 * Abstract class used to remove some repeated code along all Submodel Implementations.
 */
public abstract class AbstractSubmodel implements ISubmodel {
    private Utils utils;

    public AbstractSubmodel() {
        this.utils = new Utils();
    }

    public Utils getUtils() {
        return utils;
    }
}
