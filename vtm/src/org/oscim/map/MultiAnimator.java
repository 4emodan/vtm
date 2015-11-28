/*
 * Copyright 2013 Hannes Janetzek
 *
 * This file is part of the OpenScienceMap project (http://www.opensciencemap.org).
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.oscim.map;

import static org.oscim.core.MercatorProjection.latitudeToY;
import static org.oscim.core.MercatorProjection.longitudeToX;
import static org.oscim.utils.FastMath.clamp;

import org.oscim.backend.CanvasAdapter;
import org.oscim.core.BoundingBox;
import org.oscim.core.GeoPoint;
import org.oscim.core.MapPosition;
import org.oscim.core.Point;
import org.oscim.core.Tile;
import org.oscim.renderer.MapRenderer;
import org.oscim.utils.FastMath;
import org.oscim.utils.ThreadUtils;
import org.oscim.utils.async.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiAnimator {
	static final Logger log = LoggerFactory.getLogger(Animator.class);

	private final static int ANIM_NONE = 0;
	private final static int ANIM_MOVE = 1;
	private final static int ANIM_SCALE = 1 << 1;
	private final static int ANIM_ROTATE = 1 << 2;
	private final static int ANIM_TILT = 1 << 3;

	private final Map mMap;

	private final MapPosition mCurPos = new MapPosition();
	private final MapPosition mStartPos = new MapPosition();
	private final MapPosition mDeltaPos = new MapPosition(); // start + delta = end

	private float mMoveDuration = 500;
	private float mScaleDuration = 500;
	private float mRotateDuration = 500;
	private float mTiltDuration = 500;

	private long mMoveEnd = -1;
	private long mScaleEnd = -1;
	private long mRotateEnd = -1;
	private long mTiltEnd = -1;

	final private Object animLock = new Object();
	private final Point mPivot = new Point();
	private int mState = ANIM_NONE;

	public MultiAnimator(Map map) {
		mMap = map;
	}

	/**
	 * Animate tilt
	 *
	 * @param speed in tilt per 1000ms
	 * @param tilt new tilt
	 */
	public void animateTilt(int speed, float tilt) {
		MapPosition pos = new MapPosition();
		mMap.getMapPosition(pos);
		double delta = Math.abs(tilt - pos.getTilt());
		pos.setTilt(tilt);
		animateTo((long)(1000 * delta / speed), pos);
	}

	/**
	 * Animate zoom
	 *
	 * @param speed in zoom per 1000ms
	 * @param zoom new zoom
	 */
	public void animateZoom(int speed, double zoom) {
		MapPosition pos = new MapPosition();
		mMap.getMapPosition(pos);
		double delta = Math.abs(zoom - pos.getScale());
		pos.setScale(zoom);
		animateTo((long)(1000 * delta / speed), pos);
	}

	/**
	 * Animate bearing
	 *
	 * @param speed in bearing per 1000ms
	 * @param bearing new bearing
	 */
	public void animateBearing(int speed, float bearing) {
		MapPosition pos = new MapPosition();
		mMap.getMapPosition(pos);
		double delta = Math.abs(bearing - pos.getBearing());
		pos.setBearing(bearing);
		animateTo((long)(1000 * delta / speed), pos);
	}

	/**
	 * Animate to new position including coordinates, bearing, tilt
	 *
	 * @param duration in ms
	 * @param pos new position
	 */
	public void animateTo(long duration, MapPosition pos) {
		ThreadUtils.assertMainThread();

		int state;
		synchronized (animLock) {
			pos.scale = mMap.viewport().limitScale(pos.scale);

			MapPosition curPos = new MapPosition();
			mMap.getMapPosition(curPos); // set start pos = current pos

			MapPosition deltaPos = new MapPosition();
			deltaPos.set(pos.x - curPos.x,
					pos.y - curPos.y,
					pos.scale - curPos.scale,
					pos.bearing - curPos.bearing,
					mMap.viewport().limitTilt(pos.tilt) - curPos.tilt);

			state = 0;
			if (deltaPos.getX() != 0 || deltaPos.getY() != 0) {
				mStartPos.setX(curPos.getX());
				mStartPos.setY(curPos.getY());
				mDeltaPos.setX(deltaPos.getX());
				mDeltaPos.setY(deltaPos.getY());
				state = state | ANIM_MOVE;
				mMoveDuration = duration;
			}
			if (deltaPos.getScale() != 0) {
				mStartPos.setScale(curPos.getScale());
				mDeltaPos.setScale(deltaPos.getScale());
				state = state | ANIM_SCALE;
				mScaleDuration = duration;
			}
			if (deltaPos.getBearing() != 0) {
				mStartPos.setBearing(curPos.getBearing());
				mDeltaPos.setBearing(deltaPos.getBearing());
				state = state | ANIM_ROTATE;
				mRotateDuration = duration;
			}
			if (deltaPos.getTilt() != 0) {
				mStartPos.setTilt(curPos.getTilt());
				mDeltaPos.setTilt(deltaPos.getTilt());
				state = state | ANIM_TILT;
				mTiltDuration = duration;
			}
		}

		animStart(state);
	}

	private void animStart(int state) {
		synchronized (animLock) {
			mCurPos.copy(mStartPos); // set start pos = cur pos
			mState = mState | state;
			long curTime = System.currentTimeMillis();

			if((state & ANIM_MOVE) != 0)
				mMoveEnd = curTime + (long) mMoveDuration;

			if((state & ANIM_SCALE) != 0)
				mScaleEnd = curTime + (long) mScaleDuration;

			if((state & ANIM_ROTATE) != 0)
				mRotateEnd = curTime + (long) mRotateDuration;

			if((state & ANIM_TILT) != 0)
				mTiltEnd = curTime + (long) mTiltDuration;
		}

		mMap.render();
	}

	/**
	 * called by MapRenderer at begin of each frame.
	 */
	void updateAnimation() {
		ViewController v = mMap.viewport();

		synchronized (animLock) {
			MapPosition curPos = new MapPosition();
			curPos.copy(mCurPos);
			boolean posHasChanged = false, scaleHasChanged = false, rotationHasChanged = false, tiltHasChanged = false;
			if (v.getMapPosition(curPos)) {

				if(curPos.getX() != mCurPos.getX() || curPos.getY() != mCurPos.getY())
					posHasChanged = true;
				if(curPos.getScale() != mCurPos.getScale())
					scaleHasChanged = true;
				if(curPos.getBearing() != mCurPos.getBearing())
					rotationHasChanged = true;
				if(curPos.getTilt() != mCurPos.getTilt())
					tiltHasChanged = true;

				mCurPos.copy(curPos);
			}

			long curTime = MapRenderer.frametime;

			if (mState == ANIM_NONE)
				return;

			double scaleAdv = 1;

			if ((mState & ANIM_SCALE) != 0 && !scaleHasChanged) {
				long millisLeft = mScaleEnd - curTime;
				float adv = clamp(1.0f - millisLeft / mScaleDuration, 0, 1);

				scaleAdv = doScale(v, adv);

				if (millisLeft <= 0) {
					mState &= ~ANIM_SCALE;
					mPivot.x = 0;
					mPivot.y = 0;
				}
			}

			if ((mState & ANIM_MOVE) != 0 && !posHasChanged) {
				long millisLeft = mMoveEnd - curTime;
				float adv = clamp(1.0f - millisLeft / mMoveDuration, 0, 1);

				v.moveTo(mStartPos.x + mDeltaPos.x * (adv / scaleAdv),
						mStartPos.y + mDeltaPos.y * (adv / scaleAdv));

				if (millisLeft <= 0)
					mState &= ~ANIM_MOVE;
			}

			if ((mState & ANIM_ROTATE) != 0 && !rotationHasChanged) {
				long millisLeft = mRotateEnd - curTime;
				float adv = clamp(1.0f - millisLeft / mRotateDuration, 0, 1);

				v.setRotation(mStartPos.bearing + mDeltaPos.bearing * adv);

				if (millisLeft <= 0)
					mState &= ~ANIM_ROTATE;
			}

			if ((mState & ANIM_TILT) != 0 && !tiltHasChanged) {
				long millisLeft = mTiltEnd - curTime;
				float adv = clamp(1.0f - millisLeft / mTiltDuration, 0, 1);

				v.setTilt(mStartPos.tilt + mDeltaPos.tilt * adv);

				if (millisLeft <= 0)
					mState &= ~ANIM_TILT;
			}

			if (mState == ANIM_NONE)
				cancel();
		}

		/* remember current map position */
		final boolean changed = v.getMapPosition(mCurPos);

		if (changed) {
			mMap.updateMap(true);
		} else {
			mMap.postDelayed(updateTask, 10);
		}
	}

	private Task updateTask = new Task() {
		@Override
		public int go(boolean canceled) {
			if (!canceled)
				updateAnimation();
			return Task.DONE;
		}
	};

	private double doScale(ViewController v, float adv) {
		double newScale = mStartPos.scale + mDeltaPos.scale * Math.sqrt(adv);

		v.scaleMap((float) (newScale / mCurPos.scale),
		           (float) mPivot.x, (float) mPivot.y);

		return newScale / (mStartPos.scale + mDeltaPos.scale);
	}

	public void cancel() {
		mState = ANIM_NONE;
		mPivot.x = 0;
		mPivot.y = 0;
		mMap.events.fire(Map.ANIM_END, mMap.mMapPosition);
	}

	public boolean isActive() {
		return mState != ANIM_NONE;
	}
}