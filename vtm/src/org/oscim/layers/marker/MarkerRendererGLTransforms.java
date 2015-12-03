package org.oscim.layers.marker;


import org.oscim.core.Tile;
import org.oscim.renderer.GLState;
import org.oscim.renderer.GLViewport;
import org.oscim.renderer.bucket.RenderBucket;
import org.oscim.renderer.bucket.SymbolItem;
import org.oscim.renderer.bucket.TextureBucket;

import static org.oscim.renderer.bucket.RenderBucket.SYMBOL;

public class MarkerRendererGLTransforms extends MarkerRenderer {
    public MarkerRendererGLTransforms(MarkerLayer<MarkerItem> markerLayer, MarkerSymbol defaultSymbol) {
        super(markerLayer, defaultSymbol);
    }

    @Override
    public synchronized void render(GLViewport v) {
        GLState.test(false, false);
        GLState.blend(true);

        RenderBucket b = buckets.get();
        for (InternalItem it : mItems) {
            translateScaleRotateProject(v, it.x, it.y, it.angle);

            switch (b.type) {
                case SYMBOL:
                    buckets.bind();
                    b = TextureBucket.Renderer.draw(b, v, 1.0f);
                    break;
                default:
                    b = b.next;
                    break;
            }
        }
    }

    public synchronized void update(GLViewport v) {
        if (!v.changed() && !mUpdate)
            return;

        mUpdate = false;

        //int changesInvisible = 0;
        //int changedVisible = 0;

        mMarkerLayer.map().viewport().getMapExtents(mBox, mExtents);

        if (mItems == null) {
            if (buckets.get() != null) {
                buckets.clear();
                compile();
            }
            return;
        }

        double mx = v.pos.x;
        double my = v.pos.y;
        double scale = Tile.SIZE * v.pos.scale;
        int numVisible = countVisibleItems(v.pos.getBearing(), mx, my, scale);

        //log.debug(numVisible + " " + changedVisible + " " + changesInvisible);

		/* only update when zoomlevel changed, new items are visible
		 * or more than 10 of the current items became invisible */
        //if ((numVisible == 0) && (changedVisible == 0 && changesInvisible < 10))
        //	return;
        buckets.clear();

        if (numVisible == 0) {
            compile();
            return;
        }
		/* keep position for current state */
        mMapPosition.copy(v.pos);
        mMapPosition.bearing = -mMapPosition.bearing;

        sort(mItems, 0, mItems.length);
        //log.debug(Arrays.toString(mItems));
        for (InternalItem it : mItems) {
            if (!it.visible)
                continue;

            if (it.changes) {
                it.visible = false;
                continue;
            }

            MarkerSymbol marker = it.item.getMarker();
            if (marker == null)
                marker = mDefaultMarker;

            SymbolItem s = SymbolItem.pool.get();
            s.set(0.0f, 0.0f, marker.getBitmap(), true);
            s.offset = marker.getHotspot();
            s.billboard = marker.isBillboard();
            mSymbolLayer.pushSymbol(s);
        }

        buckets.set(mSymbolLayer);
        buckets.prepare();

        compile();
    }
}
