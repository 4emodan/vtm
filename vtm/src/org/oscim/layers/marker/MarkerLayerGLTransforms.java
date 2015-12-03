package org.oscim.layers.marker;

import org.oscim.map.Map;

import java.util.ArrayList;
import java.util.List;

public class MarkerLayerGLTransforms extends MarkerLayer<MarkerItem> {
    protected final List<MarkerItem> mItemList;
    MarkerRendererGLTransforms markerRendererGLTransforms;

    public MarkerLayerGLTransforms(Map map) {
        super(map, null);
        this.mItemList = new ArrayList<>();
        markerRendererGLTransforms = new MarkerRendererGLTransforms (this, null);
        mRenderer = markerRendererGLTransforms;
    }

    public void itemsUpdate() {
        markerRendererGLTransforms.populate(size());
    }

    @Override
    protected MarkerItem createItem(int i) {
        return mItemList.get(i);
    }

    @Override
    public int size() {
        return mItemList.size();
    }

    public boolean addItem(MarkerItem item) {
        final boolean result = mItemList.add(item);
        itemsUpdate();
        return result;
    }

    public MarkerItem getByUid(Object uid) {
        for (MarkerItem it : mItemList)
            if (it.getUid() == uid)
                return it;

        return null;
    }
}
