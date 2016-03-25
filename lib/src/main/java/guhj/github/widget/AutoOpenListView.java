package guhj.github.widget;

import android.animation.Animator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ListAdapter;

import java.util.ArrayList;
import java.util.TreeMap;

public class AutoOpenListView extends ViewGroup {
    private final long DEFAULT_DURATION = 500l;

    TreeMap<Integer, Integer> heightMap = new TreeMap<>(),//缓存到高度
            openHeightMap = new TreeMap<>();//缓存到打开高度
    OpenViewAdapter mAdapter;
    MyDataSetObserver mDataSetObserver = new MyDataSetObserver();
    int scrollTopPosition;
    int scrollY;
    View[] cacheViews;//缓存到所有View
    int itemMargin = 0;
    ViewTreeObserver.OnGlobalLayoutListener updateGlobalLayoutListener;

    //插值器
    private TimeInterpolator mInterpolator;
    private VelocityTracker mVelocityTracker;//加速度

    private ValueAnimator mScrollAnim;//动画


    //系统的手势加速度配置
    private int mTouchSlop;
    private int mMinimumVelocity;
    private int mMaximumVelocity;

    //手势数据
    private int mActivePointerId = -1;//标示活动的手指
    private int mLastMotionX, mLastMotionY;//手指最近一次的坐标
    private boolean mIsBeingDragged = false;//是否响应

    public AutoOpenListView(Context context) {
        super(context);
        init(context, null);
    }

    public AutoOpenListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public AutoOpenListView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    void init(Context context, AttributeSet attrs) {
        mInterpolator = new AccelerateDecelerateInterpolator(context, attrs);
        setFocusable(true);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        setWillNotDraw(false);
        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        mTouchSlop = configuration.getScaledTouchSlop();
        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int specSizeW = MeasureSpec.getSize(widthMeasureSpec);
        int specSizeH = MeasureSpec.getSize(heightMeasureSpec);
        super.onMeasure(MeasureSpec.makeMeasureSpec(specSizeW, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(specSizeH, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int cL, cT, cR, cB;
        View child;
        LayoutParams params;
        for (int i = 0, len = getChildCount(); i < len; i++) {
            child = getChildAt(i);
            params = getViewLayoutParams(child);

            cL = getPaddingLeft() + params.leftMargin;
            cR = cL + child.getMeasuredWidth();
            cT = params.layoutTop + params.topMargin;
            cB = cT + child.getMeasuredHeight();
            child.layout(cL, cT, cR, cB);
        }
    }

//    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        LayoutParams params = (LayoutParams) child.getLayoutParams();
        if (params.isOpen/*&& Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2 || getClipChildren()*/) {
            int restoreTo = canvas.save();
            if (params.clipMode == 0) {// centeriew
                int clipH = (int) (params.clipH * (child.getHeight() + params.topMargin + params.bottomMargin) * 0.5 + 0.5);
                canvas.clipRect(child.getLeft() - params.leftMargin, child.getTop() - params.topMargin + clipH, child.getRight() + params.rightMargin, child.getBottom() + params.bottomMargin - clipH);
            } else {// bottom
                int clipH = (int) (params.clipH * (child.getHeight() + params.topMargin + params.bottomMargin - getItemHeight(null, params.position)) + 0.5);
                canvas.clipRect(child.getLeft() - params.leftMargin, child.getTop() - params.topMargin, child.getRight() + params.rightMargin, child.getBottom() + params.bottomMargin - clipH);
            }
            boolean result = super.drawChild(canvas, child, drawingTime);
            canvas.restoreToCount(restoreTo);
            return result;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    //Touch Event
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final int action = ev.getAction();
        if (!isEnabled()) {
            return false;
        }
        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_MOVE: {
                final int activePointerId = mActivePointerId;
                if (activePointerId == -1) {
                    break;
                }
                final int pointerIndex = ev.findPointerIndex(activePointerId);
                if (pointerIndex == -1) {
                    break;
                }

                final int x = (int) ev.getX(pointerIndex);
                final int y = (int) ev.getY(pointerIndex);
                final int xDiff = (int) Math.abs(x - mLastMotionX);
                final int yDiff = (int) Math.abs(y - mLastMotionY);
                if (xDiff > mTouchSlop || yDiff > mTouchSlop) {
                    if (xDiff < yDiff) {
                        mIsBeingDragged = true;
                        mLastMotionY = y;
                        initVelocityTrackerIfNotExists();
                        mVelocityTracker.addMovement(ev);
                        if (getParent() != null)
                            getParent().requestDisallowInterceptTouchEvent(true);
                    } else {
                        if (getParent() != null)
                            getParent().requestDisallowInterceptTouchEvent(false);
                    }
                }
                break;
            }
            case MotionEvent.ACTION_DOWN: {
                final int x = (int) ev.getX();
                final int y = (int) ev.getY();
                mLastMotionX = x;
                mLastMotionY = y;
                mActivePointerId = ev.getPointerId(0);
                initOrResetVelocityTracker();
                mVelocityTracker.addMovement(ev);
                mIsBeingDragged = animIsRunning();
                break;
            }

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if (mIsBeingDragged) {
                    mIsBeingDragged = false;
                    mActivePointerId = -1;
                    toNearbyView();
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN: {
                final int index = ev.getActionIndex();
                mLastMotionX = (int) ev.getX(index);
                mLastMotionY = (int) ev.getY(index);
                mActivePointerId = ev.getPointerId(index);
                break;
            }
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                mLastMotionX = (int) ev.getX(ev.findPointerIndex(mActivePointerId));
                break;
        }
        return mIsBeingDragged;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!isEnabled()) {
            return false;
        }
        initVelocityTrackerIfNotExists();
        mVelocityTracker.addMovement(ev);

        final int action = ev.getAction();

        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: {
                if ((mIsBeingDragged = animIsRunning())) {
                    final ViewParent parent = getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                }
                if (animIsRunning()) {
                    mScrollAnim.removeAllUpdateListeners();
                    mScrollAnim.cancel();
                    mScrollAnim = null;
                }
                mLastMotionX = (int) ev.getX();
                mLastMotionY = (int) ev.getY();
                mActivePointerId = ev.getPointerId(0);
                initOrResetVelocityTracker();
                mVelocityTracker.addMovement(ev);
                break;
            }
            case MotionEvent.ACTION_POINTER_DOWN:
                final int index = ev.getActionIndex();
                mLastMotionX = (int) ev.getX(index);
                mLastMotionY = (int) ev.getY(index);
                mActivePointerId = ev.getPointerId(index);
                break;
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;
            case MotionEvent.ACTION_MOVE:
                final int activePointerIndex = ev.findPointerIndex(mActivePointerId);
                if (activePointerIndex == -1) {
                    break;
                }
                final int x = (int) ev.getX(activePointerIndex);
                final int y = (int) ev.getY(activePointerIndex);
                int deltaX = x - mLastMotionX;
                int deltaY = y - mLastMotionY;
                if (!mIsBeingDragged && (Math.abs(deltaX) > mTouchSlop || Math.abs(deltaY) > mTouchSlop)) {
                    if (Math.abs(deltaX) < Math.abs(deltaY)) {
                        final ViewParent parent = getParent();
                        if (parent != null) {
                            parent.requestDisallowInterceptTouchEvent(true);
                        }
                        mIsBeingDragged = true;
                        if (deltaY > 0) {
                            deltaY -= mTouchSlop;
                        } else {
                            deltaY += mTouchSlop;
                        }
                    } else {
                        if (getParent() != null)
                            getParent().requestDisallowInterceptTouchEvent(false);
                    }
                }
                if (mIsBeingDragged) {
                    // Scroll to follow the motion event
                    mLastMotionY = y;
                    scrollY += deltaY;
                    buildScroll();
                    if (scrollY > 0) {
                        scrollY -= deltaY * scrollY * 1.f / (double) getScrollViewHeight() * 3;
                    } else if (scrollTopPosition == mAdapter.getCount() - 1 && scrollY < 0) {
                        int bottomItemOpenHeight = getItemOpenHeight(null, scrollTopPosition);
                        int height = getHeight() - getPaddingTop() - getPaddingBottom();
                        if (bottomItemOpenHeight < height) {
                            scrollY += deltaY * scrollY / (double) getScrollViewHeight() * 3;
                        } else if (scrollY + bottomItemOpenHeight < height) {
                            scrollY += deltaY * (scrollY + bottomItemOpenHeight - height) / (double) getScrollViewHeight() * 3;
                        }
                    }
                    resetViewPositionByScrollY();
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mIsBeingDragged) {
                    final VelocityTracker velocityTracker = mVelocityTracker;
                    velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                    int initialVelocity = (int) velocityTracker.getYVelocity(mActivePointerId);

                    if (getChildCount() > 0) {
                        if ((Math.abs(initialVelocity) > mMinimumVelocity)) {
                            fling(-initialVelocity);
                        } else {
                            toNearbyView();
                        }
                    }
                    mActivePointerId = -1;
                    mIsBeingDragged = false;
                    recycleVelocityTracker();
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                if (mIsBeingDragged && getChildCount() > 0) {
                    if (mIsBeingDragged) {
                        toNearbyView();
                    }
                    mActivePointerId = -1;
                    mIsBeingDragged = false;
                    recycleVelocityTracker();
                }
                break;
        }
        return true;
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = (ev.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >>
                MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mLastMotionX = (int) ev.getX(newPointerIndex);
            mLastMotionY = (int) ev.getY(newPointerIndex);
            mActivePointerId = ev.getPointerId(newPointerIndex);
            if (mVelocityTracker != null) {
                mVelocityTracker.clear();
            }
        }
    }

    //Scroll Animation
    private int getScrollViewHeight() {
        return getMeasuredHeight() - getPaddingTop() - getPaddingBottom();
    }

    private int calEndScroll(int initialVelocity) {//计算出最大滑动距离
        int result = (int) (Math.abs(initialVelocity) * initialVelocity / getScrollViewHeight() / 150);
        return result;
    }

    private void fling(int initialVelocity) {
        if (initialVelocity > 0) {
            if (scrollTopPosition == 0 && scrollY > 0) {
                animScrollTo(0);
                return;
            }
        }
        int canScroll = canBuildScroll(-calEndScroll(initialVelocity));
        animScrollTo(canScroll);
    }

    private int canBuildScroll(int scroll) {//返回最大的边界
        int scrollY = this.scrollY + scroll;
        int scrollTopPosition = this.scrollTopPosition;
        //计算滑动后的 scrollTopPosition scrollY
        while (scrollY != 0) {
            if (scrollY > 0) {
                if (scrollTopPosition == 0) break;
                if (scrollY > itemMargin) {
                    scrollTopPosition--;
                    scrollY -= getItemOpenHeight(null, scrollTopPosition) + itemMargin;
                    if (scrollY < itemMargin) break;
                } else
                    break;
            } else {
                if (scrollTopPosition == mAdapter.getCount() - 1) break;
                int openTop = getItemOpenHeight(null, scrollTopPosition);
                if (-scrollY > openTop) {
                    scrollTopPosition++;
                    scrollY += openTop + itemMargin;
                    if (-scrollY < itemMargin) break;
                } else
                    break;
            }
        }
        if (scrollY > 0)//如果滑动后 scrollY 代表滑动到第一项
            return this.scrollY + scroll - scrollY;//回滚到 0
        int openHeiht = getItemOpenHeight(null, scrollTopPosition);//滑动后 顶部项的高度
        int height = getHeight() - getPaddingTop() - getPaddingBottom();//视图的高度
        if (openHeiht < height) {//如果item没有视图这么高
            if (-scrollY < openHeiht / 2 || scrollTopPosition == mAdapter.getCount() - 1) {//滑动没过半
                return this.scrollY + scroll - scrollY;
            } else {//滑动已过半
                return this.scrollY + scroll - scrollY - openHeiht - itemMargin;
            }
        } else {
            if (scrollTopPosition != mAdapter.getCount() - 1 && scrollY + openHeiht < height / 2) {//第一项不大于屏幕的高度一半 到下一项
                return this.scrollY + scroll - scrollY - openHeiht - itemMargin;
            } else if (openHeiht + scrollY < height) {//大于到底部
                return this.scrollY + scroll - scrollY - openHeiht + height;
            }
        }
        return this.scrollY + scroll;
    }

    private void toNearbyView() {
        if (scrollY > 0) {
            animScrollTo(0);
        } else {
            int bottomItemOpenHeight = getItemOpenHeight(null, scrollTopPosition);
            int height = getHeight() - getPaddingTop() - getPaddingBottom();
            if (bottomItemOpenHeight < height) {
                if (-scrollY < bottomItemOpenHeight / 2) {
                    animScrollTo(0);
                } else if (scrollTopPosition != mAdapter.getCount() - 1) {
                    animScrollTo(-bottomItemOpenHeight - itemMargin);
                } else {
                    animScrollTo(height - bottomItemOpenHeight);
                }
            } else {
                if (scrollTopPosition != mAdapter.getCount() - 1 && bottomItemOpenHeight + scrollY < height / 2) {
                    animScrollTo(-bottomItemOpenHeight - itemMargin);
                } else if (bottomItemOpenHeight + scrollY < height) {
                    animScrollTo(height - bottomItemOpenHeight);
                }
            }
        }
    }

    private boolean animIsRunning() {
        if (mScrollAnim == null) return false;
        return mScrollAnim.isRunning();
    }

    private void animScrollTo(int scrollY) {
        if (mScrollAnim != null && mScrollAnim.isRunning()) {
            mScrollAnim.cancel();
        }

        mScrollAnim = ValueAnimator.ofInt(this.scrollY, scrollY);
        mScrollAnim.setInterpolator(mInterpolator);
        long duration = (long) (DEFAULT_DURATION * Math.abs(scrollY - this.scrollY) / (float) (getMeasuredHeight() - getPaddingBottom() - getPaddingTop()));
        if (duration > DEFAULT_DURATION) duration = DEFAULT_DURATION;
        mScrollAnim.setDuration(duration);
        mScrollAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            int scroll = AutoOpenListView.this.scrollY;

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int value = (int) animation.getAnimatedValue();
                AutoOpenListView.this.scrollY += value - scroll;
                scroll = value;
                buildScroll();
                resetViewPositionByScrollY();
            }
        });
        mScrollAnim.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
            }

            @Override
            public void onAnimationCancel(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }
        });
        mScrollAnim.start();
    }

    // Velocity Tracker
    private void initVelocityTrackerIfNotExists() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
    }

    private void initOrResetVelocityTracker() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        } else {
            mVelocityTracker.clear();
        }
    }

    private void recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }


    //Core Scroll Build
    private void buildScroll() {
        while (scrollY != 0) {
            if (scrollY > 0) {
                if (scrollTopPosition == 0) break;
                if (scrollY > itemMargin) {
                    scrollTopPosition--;
                    scrollY -= getItemOpenHeight(null, scrollTopPosition) + itemMargin;
                    if (scrollY < itemMargin) break;
                } else
                    break;
            } else {
                if (scrollTopPosition == mAdapter.getCount() - 1) break;
                int openTop = getItemOpenHeight(null, scrollTopPosition);
                if (-scrollY > openTop) {
                    scrollTopPosition++;
                    scrollY += openTop + itemMargin;
                    if (-scrollY < itemMargin) break;
                } else
                    break;
            }
        }
    }

    private void rebuildView() {
        if (getMeasuredHeight() == 0) {
            if (updateGlobalLayoutListener != null)
                return;
            getViewTreeObserver().addOnGlobalLayoutListener(updateGlobalLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (Build.VERSION.SDK_INT > 15) {
                        getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    } else {
                        getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    }
                    updateGlobalLayoutListener = null;
                    rebuildView();
                }
            });
            return;
        }
        scrollY = 0;
        scrollTopPosition = 0;
        resetViewPositionByScrollY();
    }

    private void resetViewPositionByScrollY() {
        if (mAdapter.isEmpty()) {
            while (getChildCount() != 0) {
                cacheView(getChildAt(0));
            }
            return;
        }
        ArrayList<View> currViews = new ArrayList<View>(getChildCount());
        for (int i = 0, len = getChildCount(); i < len; i++) {
            currViews.add(getChildAt(i));
        }
        removeAllViews();

        int i = scrollTopPosition;
        int scrollTop = scrollY;
        int maxScrollDiff = getHeight() - getPaddingTop() - getPaddingBottom();
        int clipMode = 0;
        while (scrollTop < maxScrollDiff && i < mAdapter.getCount()) {
            boolean isOpen;
            float clipH = 0;
            int layoutTop;

            if (i == scrollTopPosition) {
                int itemOpenHeight = getItemOpenHeight(currViews, i);
                isOpen = true;
                if (itemOpenHeight > maxScrollDiff) {
                    if (scrollTop + itemOpenHeight > maxScrollDiff || scrollTopPosition == mAdapter.getCount() - 1) {
                        clipH = 0;
                        layoutTop = scrollTop;
                    } else {
                        clipH = (maxScrollDiff - scrollTop - itemOpenHeight) * 1.f / itemOpenHeight;
                        layoutTop = (int) (scrollTop + (itemOpenHeight * 0.5 * clipH));
                    }
                } else if (scrollTop > 0 || scrollTopPosition == mAdapter.getCount() - 1) {
                    clipH = 0;
                    layoutTop = scrollTop;
                } else {
                    clipH = -scrollTop * 1.f / itemOpenHeight;//openHeightArr.get(i);
                    layoutTop = -(int) (itemOpenHeight * 0.5 * clipH);
                }
                scrollTop += itemMargin + itemOpenHeight;
                clipMode = 0;
                i++;
            } else if (i == mAdapter.getCount() - 1) {
                int itemHeight = getItemHeight(currViews, i);
                isOpen = true;
                layoutTop = scrollTop;
                scrollTop += itemMargin + itemHeight;
                i++;
            } else if (i == scrollTopPosition + 1) {
                int itemOpenHeight = getItemOpenHeight(currViews, i);
                int itemHeight = getItemHeight(currViews, i);
                View topView = getChildAt(0);
                LayoutParams params = getViewLayoutParams(topView);
                isOpen = params.clipH != 0;
                layoutTop = scrollTop;
                if (isOpen) {
                    if (topView.getMeasuredHeight() + params.bottomMargin + params.topMargin > maxScrollDiff) {//上一项高度大于View高度
                        clipH = 1 - (topView.getMeasuredHeight() + params.bottomMargin + params.topMargin) * params.clipH / maxScrollDiff;
                    } else {
                        clipH = 1 - params.clipH;
                    }
                    clipMode = 1;
                    scrollTop += itemMargin + itemOpenHeight - (itemOpenHeight - itemHeight) * clipH;
                } else {
                    scrollTop += itemMargin + itemHeight;
                }
                i++;
            } else {
                int itemHeight = getItemHeight(currViews, i);
                isOpen = false;
                layoutTop = scrollTop;
                scrollTop += itemMargin + itemHeight;
                i++;
            }
            View v = getOrCreateView(currViews, i - 1, isOpen);
            LayoutParams params = getViewLayoutParams(v);
            params.position = i - 1;
            params.isOpen = isOpen;
            params.type = isOpen ? mAdapter.getOpenItemViewType(i - 1) : mAdapter.getItemViewType(i - 1);
            params.clipH = clipH;
            params.clipMode = clipMode;
            params.layoutTop = layoutTop;
            addView(v);
            int measureSpecW = MeasureSpec.makeMeasureSpec(getMeasuredWidth() - getPaddingLeft() - getPaddingRight() - params.leftMargin - params.rightMargin, MeasureSpec.EXACTLY);
            int measureSpecH = getChildMeasureSpec(MeasureSpec.getMode(MeasureSpec.UNSPECIFIED), params.topMargin + params.bottomMargin, params.height);
            v.measure(measureSpecW, measureSpecH);
            if (isOpen) {
                mAdapter.openViewClip(params.position, v, clipH, clipMode);
            }
        }
        for (View view : currViews) {
            cacheView(view);
        }
        requestLayout();
    }

    //height
    private int getItemOpenHeight(ArrayList<View> cacheViews, int position) {
        Integer integer = openHeightMap.get(position);
        if (integer == null) {
            View v = findOrCreateView(cacheViews, position, true);
            LayoutParams params = getViewLayoutParams(v);
            int measureSpecW = MeasureSpec.makeMeasureSpec(getMeasuredWidth() - getPaddingLeft() - getPaddingRight() - params.leftMargin - params.rightMargin, MeasureSpec.EXACTLY);
            int measureSpecH = getChildMeasureSpec(MeasureSpec.getMode(MeasureSpec.UNSPECIFIED), params.topMargin + params.bottomMargin, params.height);

            v.measure(measureSpecW, measureSpecH);
            integer = v.getMeasuredHeight() + params.bottomMargin + params.topMargin;
            openHeightMap.put(position, integer);
        }
        return integer;
    }

    private int getItemHeight(ArrayList<View> cacheViews, int position) {
        Integer integer = heightMap.get(position);
        if (integer == null) {
            View v = findOrCreateView(cacheViews, position, false);
            LayoutParams params = getViewLayoutParams(v);
            int measureSpecW = MeasureSpec.makeMeasureSpec(getMeasuredWidth() - getPaddingLeft() - getPaddingRight() - params.leftMargin - params.rightMargin, MeasureSpec.EXACTLY);
            int measureSpecH = getChildMeasureSpec(MeasureSpec.getMode(MeasureSpec.UNSPECIFIED), params.topMargin + params.bottomMargin, params.height);

            v.measure(measureSpecW, measureSpecH);
            integer = v.getMeasuredHeight() + params.bottomMargin + params.topMargin;
            heightMap.put(position, integer);
        }
        return integer;
    }

    //Cache
    private View getOrCreateView(int position, boolean isOpen) {//得到或者创建一个View
        int type = isOpen ? mAdapter.getOpenItemViewType(position) : mAdapter.getItemViewType(position);
        View v = isOpen ? mAdapter.getOpenView(position, cacheViews[type], this) : mAdapter.getView(position, cacheViews[type], this);
        if (v == cacheViews[type])
            cacheViews[type] = null;
        return v;
    }

    private View findOrCreateView(int position, boolean isOpen) {//得到或者创建一个View
        int type = isOpen ? mAdapter.getOpenItemViewType(position) : mAdapter.getItemViewType(position);
        View v = isOpen ? mAdapter.getOpenView(position, cacheViews[type], this) : mAdapter.getView(position, cacheViews[type], this);
        if (v != cacheViews[type]) {
            cacheViews[type] = v;
        }
        return v;
    }

    private View getOrCreateView(ArrayList<View> currViews, int position, boolean isOpen) {
        View v = null;
        for (int i = 0, len = currViews.size(); i < len; i++) {//第一次判断该View是否存在 存在则不重绘
            View v2 = currViews.get(i);
            LayoutParams params = getViewLayoutParams(v2);
            if (params.position == position && isOpen == params.isOpen) {
                currViews.remove(i);
                return v2;
            }
        }
        int type = isOpen ? mAdapter.getOpenItemViewType(position) : mAdapter.getItemViewType(position);
        for (int i = 0, len = currViews.size(); i < len; i++) {//第二次使用其他的View
            View v2 = currViews.get(i);
            LayoutParams params = getViewLayoutParams(v2);
            if (params.type == type) {
                v = isOpen ? mAdapter.getOpenView(position, v2, this) : mAdapter.getView(position, v2, this);
                if (v == v2) {
                    currViews.remove(i);
                }
                break;
            }
        }
        if (v == null)
            v = getOrCreateView(position, isOpen);
        return v;
    }

    private View findOrCreateView(ArrayList<View> cacheViews, int position, boolean isOpen) {
        if (cacheViews == null) {
            return findOrCreateView(position, isOpen);
        }
        View v = null;
        for (int i = 0, len = cacheViews.size(); i < len; i++) {//第一次判断该View是否存在 存在则不重绘
            View v2 = cacheViews.get(i);
            LayoutParams params = getViewLayoutParams(v2);
            if (params.isOpen == isOpen && params.position == position) {
                return v2;
            }
        }
        int type = isOpen ? mAdapter.getOpenItemViewType(position) : mAdapter.getItemViewType(position);
        for (int i = 0, len = cacheViews.size(); i < len; i++) {//第二次使用其他的View
            View v2 = cacheViews.get(i);
            LayoutParams params = getViewLayoutParams(v2);
            if (params.type == type) {
                v = isOpen ? mAdapter.getOpenView(position, v2, this) : mAdapter.getView(position, v2, this);
                break;
            }
        }
        if (v == null)
            v = findOrCreateView(position, isOpen);
        return v;
    }

    private void cacheView(View v) {//缓存一个View
        if (v.getParent() != null) {
            removeView(v);
        }
        LayoutParams params = getViewLayoutParams(v);
        cacheViews[params.type] = v;
    }

    // LayoutParams
    public LayoutParams getViewLayoutParams(View v) {
        ViewGroup.LayoutParams params = v.getLayoutParams();
        if (params instanceof LayoutParams)
            return (LayoutParams) params;
        LayoutParams params1 = new LayoutParams(params);
        v.setLayoutParams(params1);
        return params1;
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    private static class LayoutParams extends ViewGroup.MarginLayoutParams {
        public int position;//第几项
        public int type;//类型
        int layoutTop;//布局时的顶部和底部

        public boolean isOpen;//是否打开
        public float clipH;//切除多少
        public int clipMode;//0 center 1 bottom

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }
    }

    //DataSetObserver
    private class MyDataSetObserver extends DataSetObserver {
        @Override
        public void onChanged() {
            rebuildView();
        }

        @Override
        public void onInvalidated() {
            rebuildView();
        }
    }


    //Adapter
    public void setAdapter(OpenViewAdapter adapter) {
        if (mAdapter != null) {
            cacheViews = null;
            heightMap.clear();
            removeAllViews();
            mAdapter.unregisterDataSetObserver(mDataSetObserver);
        }
        mAdapter = adapter;
        if (mAdapter != null) {
            mAdapter.registerDataSetObserver(mDataSetObserver);
            cacheViews = new View[mAdapter.getViewTypeCount()];
            rebuildView();
        }
    }

    public OpenViewAdapter getAdapter(){
        return mAdapter;
    }

    public interface OpenViewAdapter extends ListAdapter {
        View getOpenView(int position, View convertView, ViewGroup parent);

        int getOpenItemViewType(int position);

        void openViewClip(int position, View itemView, float clip, int clipMode);//0 center 1 bottom
    }

    //ScrollTopPosition
    public int getScrollTopPosition(){
        return scrollTopPosition;
    }

    public void setScrollTopPosition(int position){
        scrollTopPosition = position;
        scrollTopPosition = 0;
        resetViewPositionByScrollY();
    }

    public interface OpenViewListener{
        void onScrollTopPositonChangedListener(int position);
    }
}
