package skyestudios.buildx.othereditor.android;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;



import java.util.List;

import android.view.inputmethod.CursorAnchorInfo;
import android.graphics.Matrix;
import android.widget.OverScroller;
import android.graphics.Color;

import skyestudios.buildx.othereditor.common.Content;
import skyestudios.buildx.othereditor.common.Cursor;
import skyestudios.buildx.othereditor.common.TextColorProvider;
import skyestudios.buildx.othereditor.interfaces.ContentListener;
import skyestudios.buildx.othereditor.interfaces.EditorLanguage;
import skyestudios.buildx.othereditor.interfaces.Indexer;
import skyestudios.buildx.othereditor.langs.EmptyLanguage;
import skyestudios.buildx.othereditor.simpleclass.BlockLine;
import skyestudios.buildx.othereditor.simpleclass.Span;
import skyestudios.buildx.othereditor.utils.Clipboard;
import skyestudios.buildx.othereditor.utils.IClipboard;

/**
 * RoseEditor is a editor that can highlight texts region by doing basic grammar analyzing
 * Actually it can adapt to Android level 11
 *
 * Thanks following people for advice on UI:
 * NTX
 * 吾乃幼儿园扛把子
 * Xiue
 * Scave
 *
 * @author Rose
 */
public class RoseEditor extends View implements ContentListener, TextColorProvider.Callback {

    private static final String LOG_TAG = "RoseEditor";

    /**
     * The default size when creating the editor object.Unit is sp.
     */
    public static final int DEFAULT_TEXT_SIZE = 16;

    private int mTabWidth;
    private int mCursorPosition;
    private int mMinModifiedLine;
    private float mDpUnit;
    private float mMaxPaintX;
    private float mSpaceWidth;
    private float mDividerWidth;
    private float mDividerMargin;
    private float mInsertSelWidth;
    private float mUnderlineWidth;
    private float mBlockLineWidth;
    private boolean mWait;
    private boolean mDrag;
    private boolean mScale;
    private boolean mEditable;
    private boolean mAutoIndent;
    private boolean mPaintLabel;
    private boolean mUndoEnabled;
    private boolean mDisplayLnPanel;
    private boolean mHighlightCurrentBlock;
    private boolean mVerticalScrollBarEnabled;
    private boolean mHorizontalScrollBarEnabled;
    private boolean mVerticalScrollBarSizeEnlarged;
    private RectF mRect;
    private RectF mLeftHandle;
    private RectF mRightHandle;
    private RectF mInsertHandle;
    private RectF mVerticalScrollBar;
    private RectF mHorizontalScrollBar;
    private Typeface mTypefaceText;
    private Typeface mTypefaceLineNumber;
    private IClipboard mClipboardManager;
    private InputMethodManager mInputMethodManager;

    private Cursor mCursor;
    private Content mText;
    private TextColorProvider mSpanner;

    private Paint mPaint;
    private char[] mChars;
    private Matrix mMatrix;
    private Rect mViewRect;
    private ColorScheme mColors;
    private String mLnTip = "行:";
    private EditorLanguage mLanguage;
    private long mLastMakeVisible = 0;
    private AutoCompletePanel mACPanel;
    private EventHandler mEventHandler;
    private Paint.Align mLineNumberAlign;
    private GestureDetector mBasicDetector;
    private TextComposePanel mTextActionPanel;
    private ScaleGestureDetector mScaleDetector;
    private RoseEditorInputConnection mConnection;
    private CursorAnchorInfo.Builder mAnchorInfoBuilder;

    /**
     * Cancel invalidate() calls when formatting
     */
    private boolean mCancelForFormatting = false;

    //For debug
    private StringBuilder mErrorBuilder = new StringBuilder();
    private long m;

    /**
     * Create a new editor
     * @param context Your activity
     */
    public RoseEditor(Context context) {
        super(context);
        prepare();
    }

    public RoseEditor(Context context, AttributeSet attrs) {
        super(context, attrs);
        prepare();
    }

    public RoseEditor(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        prepare();
    }

    @TargetApi(21)
    public RoseEditor(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        prepare();
    }

    /**
     * Cancel the next animation for {@link RoseEditor#makeCharVisible(int, int)}
     */
    protected void cancelAnimation() {
        mLastMakeVisible = System.currentTimeMillis();
    }

    /**
     * Get the rect of left selection handle painted on view
     * @return Rect of left handle
     */
    protected RectF getLeftHandleRect() {
        return mLeftHandle;
    }

    /**
     * Get the rect of right selection handle painted on view
     * @return Rect of right handle
     */
    protected RectF getRightHandleRect() {
        return mRightHandle;
    }

    /**
     * Whether the editor should use a different color to draw
     * the current code block line and this code block's start line and end line's
     * background.
     * @param highlightCurrentBlock Enabled / Disabled this module
     */
    public void setHighlightCurrentBlock(boolean highlightCurrentBlock){
        this.mHighlightCurrentBlock = highlightCurrentBlock;
        if(!mHighlightCurrentBlock) {
            mCursorPosition = -1;
        }else{
            mCursorPosition = findCursorBlock();
        }
        invalidate();
    }

    /**
     * Returns whether highlight current code block
     * @see RoseEditor#setHighlightCurrentBlock(boolean)
     * @return This module enabled / disabled
     */
    public boolean isHighlightCurrentBlock(){
        return mHighlightCurrentBlock;
    }

    /**
     * Whether we should use a different color to draw current code block's start line and end line background
     * @param paintLabel Enabled or disabled
     */
    public void setPaintLabel(boolean paintLabel){
        this.mPaintLabel = paintLabel;
        invalidate();
    }

    /**
     * Get the width of line number and divider line
     * @return The width
     */
    protected float measurePrefix() {
        return measureLineNumber() + mDividerMargin * 2 + mDividerWidth;
    }

    /**
     * @see RoseEditor#setPaintLabel(boolean)
     * @return Enabled / disabled
     */
    public boolean isPaintLabel(){
        return mPaintLabel;
    }

    /**
     * Prepare editor
     * Initialize variants
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void prepare(){
        mPaint = new Paint();
        mMatrix = new Matrix();
        //Only Android.LOLLIPOP and upper level device can use this builder
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mAnchorInfoBuilder = new CursorAnchorInfo.Builder();
        }
        mPaint.setAntiAlias(true);
        setTextSize(DEFAULT_TEXT_SIZE);
        mColors = new ColorScheme(this);
        mEventHandler = new EventHandler(this);
        mBasicDetector = new GestureDetector(getContext(), mEventHandler);
        mBasicDetector.setOnDoubleTapListener(mEventHandler);
        mScaleDetector = new ScaleGestureDetector(getContext(), mEventHandler);
        mViewRect = new Rect(0, 0, 0, 0);
        mRect = new RectF();
        mInsertHandle = new RectF();
        mLeftHandle = new RectF();
        mRightHandle = new RectF();
        mVerticalScrollBar = new RectF();
        mHorizontalScrollBar = new RectF();
        mDividerMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, Resources.getSystem().getDisplayMetrics());
        mDividerWidth = mDividerMargin;
        mUnderlineWidth = mDividerMargin;
        mInsertSelWidth = mDividerWidth / 2;
        mDpUnit = mInsertSelWidth;
        mLineNumberAlign = Paint.Align.RIGHT;
        mTypefaceLineNumber = Typeface.MONOSPACE;
        mTypefaceText = Typeface.DEFAULT;
        mChars = new char[256];
        mEditable = true;
        mScale = true;
        mDrag = false;
        mWait = false;
        mPaintLabel = true;
        mDisplayLnPanel = true;
        mBlockLineWidth = 2;
        mInputMethodManager = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        mClipboardManager = Clipboard.getClipboard(getContext());
        setUndoEnabled(true);
        mTabWidth = 4;
        mAutoIndent = true;
        mCursorPosition = -1;
        mHighlightCurrentBlock = true;
        setFocusable(true);
        setFocusableInTouchMode(true);
        mConnection = new RoseEditorInputConnection(this);
        mACPanel = new AutoCompletePanel(this);
        mTextActionPanel = new TextComposePanel(this);
        mTextActionPanel.setHeight((int)(mDpUnit * 60));
        mTextActionPanel.setWidth((int)(mDpUnit * 230));
        setEditorLanguage(null);
        setText(null);
    }

    /**
     * Set the editor's language.
     * A language is a for auto completion,highlight and auto indent analysis.
     * @param lang New EditorLanguage for editor
     */
    public void setEditorLanguage(EditorLanguage lang) {
        if(lang == null) {
            lang = new EmptyLanguage();
        }
        this.mLanguage = lang;
        if(mSpanner != null) {
            mSpanner.setCallback(null);
        }
        mSpanner = new TextColorProvider(lang.createAnalyzer());
        mSpanner.setCallback(this);
        if(mText != null) {
            mSpanner.analyze(mText);
        }
        if(mACPanel != null) {
            mACPanel.hide();
            mACPanel.setProvider(lang.createAutoComplete());
        }
        if(mCursor != null){
            mCursor.setLanguage(mLanguage);
        }
        invalidate();
    }

    /**
     * Set the width of code block line
     * @param dp Width in dp unit
     */
    public void setBlockLineWidth(float dp) {
        mBlockLineWidth = dp;
        invalidate();
    }

    /**
     * Get the character's x offset on view
     * @param line The line position of character
     * @param column The column position of character
     * @return The x offset on screen
     */
    protected float getOffset(int line,int column) {
        prepareLine(line);
        mPaint.setTypeface(mTypefaceText);
        return measureText(mChars,0,column) + measureLineNumber() + mDividerMargin * 2 + mDividerWidth - getOffsetX();
    }

    /**
     * Getter
     * @see RoseEditor#setBlockLineWidth(float)
     * @return The width in dp unit
     */
    public float getBlockLineWidth() {
        return mBlockLineWidth;
    }

    /**
     * Whether display vertical scroll bar when scrolling
     * @param enabled Enabled / disabled
     */
    public void setScrollBarEnabled(boolean enabled) {
        mVerticalScrollBarEnabled = mHorizontalScrollBarEnabled = enabled;
        invalidate();
    }

    /**
     * Whether display the line number panel beside vertical scroll bar
     * when the scroll bar is touched by user
     * @param displayLnPanel Enabled / disabled
     */
    public void setDisplayLnPanel(boolean displayLnPanel){
        this.mDisplayLnPanel = displayLnPanel;
        invalidate();
    }

    /**
     * @see RoseEditor#setDisplayLnPanel(boolean)
     * @return Enabled / disabled
     */
    public boolean isDisplayLnPanel(){
        return mDisplayLnPanel;
    }

    /**
     * Get TextComposePanel instance of this editor
     * @return TextComposePanel
     */
    protected TextComposePanel getTextActionPanel() {
        return mTextActionPanel;
    }

    /**
     * Set the tip text before line number for the line number panel
     * @param prefix The prefix for text
     */
    public void setLnTip(String prefix) {
        if(prefix == null) {
            prefix = "";
        }
        mLnTip = prefix;
        invalidate();
    }

    /**
     * @see RoseEditor#setLnTip(String)
     * @return The prefix
     */
    public String getLnTip() {
        return mLnTip;
    }

    /**
     * Set whether auto indent should be executed when user enters
     * a NEWLINE
     * @param enabled Enabled / disabled
     */
    public void setAutoIndent(boolean enabled) {
        mAutoIndent = enabled;
        mCursor.setAutoIndent(enabled);
    }

    /**
     * @see RoseEditor#setAutoIndent(boolean)
     * @return Enabled / disabled
     */
    public boolean isAutoIndent() {
        return mAutoIndent;
    }

    @Override
    public void setHorizontalScrollBarEnabled(boolean horizontalScrollBarEnabled){
        mHorizontalScrollBarEnabled = horizontalScrollBarEnabled;
    }

    @Override
    public void setVerticalScrollBarEnabled(boolean verticalScrollBarEnabled){
        mVerticalScrollBarEnabled = verticalScrollBarEnabled;
    }

    @Override
    public boolean isHorizontalScrollBarEnabled(){
        return mHorizontalScrollBarEnabled;
    }

    @Override
    public boolean isVerticalScrollBarEnabled(){
        return mVerticalScrollBarEnabled;
    }

    /**
     * Get the rect of vertical scroll bar on view
     * @return Rect of scroll bar
     */
    protected RectF getVerticalScrollBarRect(){
        return mVerticalScrollBar;
    }

    /**
     * Get the rect of horizontal scroll bar on view
     * @return Rect of scroll bar
     */
    protected RectF getHorizontalScrollBarRect() {
        return mHorizontalScrollBar;
    }

    /**
     * Get the rect of insert cursor handle on view
     * @return Rect of insert handle
     */
    protected RectF getInsertHandleRect() {
        return mInsertHandle;
    }

    /**
     * Set text size in pixel unit
     * @param size Text size in pixel unit
     */
    public void setTextSizePx(float size) {
        mPaint.setTextSize(size);
        mSpaceWidth = mPaint.measureText(" ");
        invalidate();
    }

    /**
     * Get text size in pixel unit
     * @see RoseEditor#setTextSize(float)
     * @see RoseEditor#setTextSizePx(float)
     * @return Text size in pixel unit
     */
    public float getTextSizePx() {
        return mPaint.getTextSize();
    }

    /**
     * Paint the view on given Canvas
     * @param canvas Canvas you want to draw
     */
    private void drawView(Canvas canvas){
        //long start = System.currentTimeMillis();

        getCursor().updateCache(getFirstVisibleLine());

        ColorScheme color = mColors;
        drawColor(canvas, color.getColor(ColorScheme.WHOLE_BACKGROUND), mViewRect);

        float lineNumberWidth = measureLineNumber();
        float offsetX = - getOffsetX();

        drawLineNumberBackground(canvas, offsetX, lineNumberWidth + mDividerMargin, color.getColor(ColorScheme.LINE_NUMBER_BACKGROUND));

        drawCurrentLineBackground(canvas, color.getColor(ColorScheme.CURRENT_LINE));
        drawCurrentCodeBlockLabelBg(canvas);

        drawDivider(canvas, offsetX + lineNumberWidth + mDividerMargin, color.getColor(ColorScheme.LINE_DIVIDER));

        drawLineNumbers(canvas, offsetX, lineNumberWidth, color.getColor(ColorScheme.LINE_NUMBER));
        offsetX += lineNumberWidth + mDividerMargin * 2 + mDividerWidth;

        if(mCursor.isSelected()){
            drawSelectedTextBackground(canvas,offsetX, color.getColor(ColorScheme.SELECTED_TEXT_BACKGROUND));
        }

        drawText(canvas, offsetX, color.getColor(ColorScheme.TEXT_NORMAL));
        drawComposingTextUnderline(canvas,offsetX, color.getColor(ColorScheme.UNDERLINE));

        if(!mCursor.isSelected()){
            drawSelectionInsert(canvas, offsetX, color.getColor(ColorScheme.SELECTION_INSERT));
            if(mEventHandler.shouldDrawInsertHandle()) {
                drawHandle(canvas,mCursor.getLeftLine(),mCursor.getLeftColumn(),mInsertHandle);
            }
        }else if(!mTextActionPanel.isShowing()){
            drawHandle(canvas,mCursor.getLeftLine(),mCursor.getLeftColumn(),mLeftHandle);
            drawHandle(canvas,mCursor.getRightLine(),mCursor.getRightColumn(),mRightHandle);
        }else{
            mLeftHandle.setEmpty();
            mRightHandle.setEmpty();
        }

        drawBlockLines(canvas,offsetX);
        drawScrollBars(canvas);

        //These are for debug
        //long end = System.currentTimeMillis();
        //canvas.drawText("Draw:" + (end - start) + "ms" + "Last highlight:" + m + "ms", 0, getLineBaseLine(11), mPaint);
    }

    //These are building
	/*
	private void drawOverScrollEdge(Canvas canvas){
		if(!getScroller().isOverScrolled()) {
			return;
		}
		int stateX = getOverScrollState(getScrollMaxX(),getOffsetX());
		if(stateX != 0) {

		}
	}
	private int getOverScrollState(int max,int curr) {
		if(curr < 0) {
			return 1;
		}else if(curr > max) {
			return 2;
		}
		return 0;
	}*/

    /**
     * Draw a handle.
     * The handle can be insert handle,left handle or right handle
     * @param canvas The Canvas to draw handle
     * @param line The line you want to attach handle to its bottom (Usually the selection line)
     * @param column The column you want to attach handle center to its center x offset
     * @param resultRect The rect of handle this method drew
     */
    private void drawHandle(Canvas canvas,int line,int column,RectF resultRect) {
        float radius = mDpUnit * 12;
        float top = getLineBottom(line) - getOffsetY();
        float bottom = top + radius * 2;
        prepareLine(line);
        float centerX = measureLineNumber() + mDividerMargin * 2 + mDividerWidth + measureText(mChars,0,column) - getOffsetX();
        float left = centerX - radius;
        float right = centerX + radius;
        if(right < 0 || left > getWidth() || bottom < 0 || top > getHeight()) {
            resultRect.setEmpty();
            return;
        }
        resultRect.left = left;
        resultRect.right = right;
        resultRect.top = top;
        resultRect.bottom = bottom;
        mPaint.setColor(mColors.getColor(ColorScheme.SELECTION_HANDLE));
        canvas.drawCircle(centerX,(top + bottom) / 2,radius,mPaint);
    }

    /**
     * Whether this region has visible region on screen
     * @param begin The begin line of code block
     * @param end The end line of code block
     * @param first The first visible line on screen
     * @param last The last visible line on screen
     * @return Whether this block can be seen
     */
    private static boolean hasVisibleRegion(int begin,int end,int first,int last) {
        return (end > first && begin < last);
    }

    /**
     * Draw code block lines on screen
     * @param canvas The canvas to draw
     * @param offsetX The start x offset for text
     */
    private void drawBlockLines(Canvas canvas,float offsetX) {
        List<BlockLine> blocks = mSpanner == null ? null : mSpanner.getColors().getBlocks();
        if(blocks == null || blocks.isEmpty()) {
            return;
        }
        int first = getFirstVisibleLine();
        int last = getLastVisibleLine();
        boolean mark = false;
        int jCount = 0;
        int maxCount = Integer.MAX_VALUE;
        if(mSpanner != null) {
            TextColorProvider.TextColors colors = mSpanner.getColors();
            if(colors != null) {
                maxCount = colors.getSuppressSwitch();
            }
        }
        int mm = binarySearchEndBlock(first,blocks);
        int cursorIdx = mCursorPosition;
        for(int curr = mm;curr < blocks.size();curr++){
            BlockLine block = blocks.get(curr);
            if(hasVisibleRegion(block.startLine,block.endLine,first,last)){
                try{
                    prepareLine(block.endLine);
                    float offset1 = measureText(mChars,0,block.endColumn);
                    prepareLine(block.startLine);
                    float offset2 = measureText(mChars,0,block.startColumn);
                    float offset = Math.min(offset1,offset2);
                    float centerX = offset + offsetX;
                    mRect.top = Math.max(0,getLineBottom(block.startLine) - getOffsetY());
                    mRect.bottom = Math.min(getHeight(),getLineTop(block.endLine) - getOffsetY());
                    mRect.left = centerX - mDpUnit * mBlockLineWidth / 2;
                    mRect.right = centerX + mDpUnit * mBlockLineWidth / 2;
                    drawColor(canvas,mColors.getColor(curr == cursorIdx ? ColorScheme.BLOCK_LINE_CURRENT :ColorScheme.BLOCK_LINE),mRect);
                }catch(IndexOutOfBoundsException e) {
                    e.printStackTrace();
                    //Not handled.
                    //Because the exception usually occurs when the content changed.
                }
                mark = true;
            }else if(mark) {
                if(jCount >= maxCount)
                    break;
                jCount++;
            }
        }
    }

    /**
     * Find the smallest code block that cursor is in
     * @return The smallest code block index.
     *          If cursor is not in any code block,just -1.
     */
    private int findCursorBlock() {
        List<BlockLine> blocks = mSpanner == null ? null : mSpanner.getColors().getBlocks();
        if(blocks == null || blocks.isEmpty()) {
            return -1;
        }
        return findCursorBlock(blocks);
    }

    /**
     * Find the cursor code block internal
     * @param blocks Current code blocks
     * @return The smallest code block index.
     *         If cursor is not in any code block,just -1.
     */
    private int findCursorBlock(List<BlockLine> blocks) {
        int line = mCursor.getLeftLine();
        int min = binarySearchEndBlock(line,blocks);
        int max = blocks.size() - 1;
        int minDis = Integer.MAX_VALUE;
        int found = -1;
        int jCount = 0;
        int maxCount = Integer.MAX_VALUE;
        if(mSpanner != null) {
            TextColorProvider.TextColors colors = mSpanner.getColors();
            if(colors != null) {
                maxCount = colors.getSuppressSwitch();
            }
        }
        for(int i = min;i <= max;i++) {
            BlockLine block = blocks.get(i);
            if(block.endLine >= line && block.startLine <= line) {
                int dis = block.endLine - block.startLine;
                if(dis < minDis) {
                    minDis = dis;
                    found = i;
                }
            }else if(minDis != Integer.MAX_VALUE) {
                jCount++;
                if(jCount >= maxCount) {
                    break;
                }
            }
        }
        return found;
    }

    /**
     * Find the first code block that maybe seen on screen
     * Because the code blocks is sorted by its end line position
     * we can use binary search to quicken this process in order to decrease
     * the time we use on finding
     * @param firstVis The first visible line
     * @param blocks Current code blocks
     * @return The block we found.Always a valid index(Unless there is no block)
     */
    private int binarySearchEndBlock(int firstVis,List<BlockLine> blocks) {
        //end > firstVis
        int left = 0,right = blocks.size() - 1,mid,row;
        int max = right;
        while(left <= right){
            mid = (left + right) / 2;
            if(mid < 0) return 0;
            if(mid > max) return max;
            row =  blocks.get(mid).endLine;
            if(row > firstVis) {
                right = mid - 1;
            }else if(row < firstVis) {
                left = mid + 1;
            }else{
                left = mid;
                break;
            }
        }
        return Math.max(0,Math.min(left,max));
    }

    /**
     * Draw scroll bars and tracks
     * @param canvas The canvas to draw
     */
    private void drawScrollBars(Canvas canvas) {
        if(!mEventHandler.shouldDrawScrollBar()){
            return;
        }
        mVerticalScrollBar.setEmpty();
        mHorizontalScrollBar.setEmpty();
        if(getScrollMaxY() > getHeight()/2) {
            drawScrollBarTrackVertical(canvas,10);
        }
        if(getScrollMaxX() > getWidth()*3/4) {
            drawScrollBarTrackHorizontal(canvas,10);
        }
        if(getScrollMaxY() > getHeight()/2) {
            drawScrollBarVertical(canvas,10);
        }
        if(getScrollMaxX() > getWidth()*3/4) {
            drawScrollBarHorizontal(canvas,10);
        }
    }

    /**
     * Draw vertical scroll bar track
     * @param canvas Canvas to draw
     * @param width The size of scroll bar,dp unit
     */
    private void drawScrollBarTrackVertical(Canvas canvas,int width) {
        if(mEventHandler.holdVerticalScrollBar()) {
            mRect.right = getWidth();
            mRect.left = getWidth() - mDpUnit * width;
            mRect.top = 0;
            mRect.bottom = getHeight();
            drawColor(canvas,mColors.getColor(ColorScheme.SCROLL_BAR_TRACK),mRect);
        }
    }

    /**
     * Draw vertical scroll bar
     * @param canvas Canvas to draw
     * @param width The size of scroll bar,dp unit
     */
    private void drawScrollBarVertical(Canvas canvas,int width) {
        int page = getHeight();
        float all = getLineHeight() * getLineCount() + getHeight() / 2;
        float length = page / all * getHeight();
        float topY;
        if(length < mDpUnit * 30) {
            mVerticalScrollBarSizeEnlarged = true;
            length = mDpUnit * 30;
            topY = (getOffsetY() + page / 2f) / all * (getHeight() - length);
        }else{
            topY = getOffsetY() / all * getHeight();
            mVerticalScrollBarSizeEnlarged = false;
        }
        if(mEventHandler.holdVerticalScrollBar()) {
            float centerY = topY + length / 2f;
            drawLineInfoPanel(canvas,centerY,mRect.left - mDpUnit * 5);
        }
        mRect.right = getWidth();
        mRect.left = getWidth() - mDpUnit * width;
        mRect.top = topY;
        mRect.bottom = topY + length;
        mVerticalScrollBar.set(mRect);
        drawColor(canvas,mColors.getColor(mEventHandler.holdVerticalScrollBar() ? ColorScheme.SCROLL_BAR_THUMB_DOWN : ColorScheme.SCROLL_BAR_THUMB),mRect);
    }

    /**
     * Draw line number panel
     * @param canvas Canvas to draw
     * @param centerY The center y on screen for the panel
     * @param rightX The right x on screen for the panel
     */
    private void drawLineInfoPanel(Canvas canvas,float centerY,float rightX) {
        if(!mDisplayLnPanel) {
            return;
        }
        float expand = mDpUnit * 3;
        String text = mLnTip + ((2 + getFirstVisibleLine() + getLastVisibleLine() / 2));
        float textWidth = mPaint.measureText(text);
        mRect.top = centerY - getLineHeight() / 2f - expand;
        mRect.bottom = centerY + getLineHeight() / 2f + expand;
        mRect.right = rightX;
        mRect.left = rightX - expand * 2 - textWidth;
        drawColor(canvas,mColors.getColor(ColorScheme.LINE_NUMBER_PANEL),mRect);
        float baseline = centerY - getLineHeight()/2 + getLineBaseLine(0);
        float centerX = (mRect.left + mRect.right) / 2;
        mPaint.setColor(mColors.getColor(ColorScheme.LINE_NUMBER_PANEL_TEXT));
        Paint.Align align = mPaint.getTextAlign();
        mPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(text,centerX,baseline,mPaint);
        mPaint.setTextAlign(align);
    }

    /**
     * Draw horizontal scroll bar track
     * @param canvas Canvas to draw
     * @param width The size of scroll bar,dp unit
     */
    private void drawScrollBarTrackHorizontal(Canvas canvas,int width) {
        if(mEventHandler.holdHorizontalScrollBar()) {
            mRect.top = getHeight() - mDpUnit * width;
            mRect.bottom = getHeight();
            mRect.right = getWidth();
            mRect.left = 0;
            drawColor(canvas,mColors.getColor(ColorScheme.SCROLL_BAR_TRACK),mRect);
        }
    }

    /**
     * Draw horizontal scroll bar
     * @param canvas Canvas to draw
     * @param width The size of scroll bar,dp unit
     */
    private void drawScrollBarHorizontal(Canvas canvas,int width) {
        int page = getWidth();
        float all = getScrollMaxX() + getWidth();
        float length = page / all * getWidth();
        float leftX = getOffsetX() / all * getWidth();
        mRect.top = getHeight() - mDpUnit * width;
        mRect.bottom = getHeight();
        mRect.right = leftX + length;
        mRect.left = leftX;
        mHorizontalScrollBar.set(mRect);
        drawColor(canvas,mColors.getColor(mEventHandler.holdHorizontalScrollBar() ? ColorScheme.SCROLL_BAR_THUMB_DOWN : ColorScheme.SCROLL_BAR_THUMB),mRect);
    }

    /**
     * Draw text background for text
     * @param canvas Canvas to draw
     * @param offsetX Start x of text region
     * @param color Color of text background
     */
    private void drawSelectedTextBackground(Canvas canvas,float offsetX,int color){
        int startLine = mCursor.getLeftLine();
        int endLine = mCursor.getRightLine();
        int leftLine = startLine;
        int rightLine = endLine;
        if(startLine < getFirstVisibleLine()) {
            startLine = getFirstVisibleLine();
        }
        if(endLine > getLastVisibleLine()) {
            endLine = getLastVisibleLine();
        }
        if(startLine < 0) {
            startLine = 0;
        }
        if(endLine > getLineCount()) {
            endLine = getLineCount() - 1;
        }
        for(int line = startLine;line <= endLine;line++){
            int start = 0,end = getText().getColumnCount(line);
            if(line == leftLine){
                start = mCursor.getLeftColumn();
            }
            if(line == rightLine){
                end = mCursor.getRightColumn();
            }
            mRect.top = getLineTop(line) - getOffsetY();
            mRect.bottom = mRect.top + getLineHeight();
            prepareLine(line);
            mRect.left = offsetX + measureText(mChars,0,start);
            mRect.right = mRect.left + measureText(mChars,start,end - start) + mDpUnit * 3;
            drawColor(canvas,color,mRect);
        }
    }

    /**
     * Draw a underline for composing text
     * @param canvas Canvas to draw
     * @param offsetX The start x of text region
     * @param color The color of underline
     */
    private void drawComposingTextUnderline(Canvas canvas,float offsetX,int color){
        if(mConnection != null && mConnection.composingLine != -1){
            int offY = getLineBottom(mConnection.composingLine) - getOffsetY();
            prepareLine(mConnection.composingLine);
            offsetX += measureText(mChars,0,mConnection.composingStart);
            float width = measureText(mChars,mConnection.composingStart,mConnection.composingEnd - mConnection.composingStart);
            mRect.top = offY - mUnderlineWidth;
            mRect.bottom = offY;
            mRect.left = offsetX;
            mRect.right = offsetX + width;
            drawColor(canvas,color,mRect);
        }
    }

    /**
     * Draw a line as insert cursor
     * @param canvas Canvas to draw
     * @param offsetX Start x of text region
     * @param color Color of cursor
     */
    private void drawSelectionInsert(Canvas canvas, float offsetX, int color){
        int line = mCursor.getLeftLine();
        if(isLineVisible(line)){
            int column = mCursor.getLeftColumn();
            prepareLine(line);
            float width = measureText(mChars, 0, column);
            mRect.top = getLineTop(line) - getOffsetY();
            mRect.bottom = getLineBottom(line) - getOffsetY();
            mRect.left = offsetX + width;
            mRect.right = offsetX + width + mInsertSelWidth;
            drawColor(canvas, color, mRect);
        }
    }

    /**
     * Draw text for the view
     * @param canvas Canvas to draw
     * @param offsetX Start x of text region
     * @param defaultColor Default color for no spans
     */
    private void drawText(Canvas canvas, float offsetX, int defaultColor){
        TextColorProvider.TextColors colors = mSpanner == null ? null : mSpanner.getColors();
        if(colors == null || colors.getSpans().isEmpty()) {
            drawTextDirect(canvas, offsetX, defaultColor, getFirstVisibleLine());
        }else{
            drawTextBySpans(canvas, offsetX, colors);
        }
    }

    /**
     * Check whether the given character is a start sign for emoji
     * @param ch Character to check
     * @return Whether this is leading a emoji
     */
    private static boolean isEmoji(char ch){
        return ch == 0xd83c || ch == 0xd83d;
    }

    /**
     * Measure text width
     * @param src Source characters array
     * @param index Start index in array
     * @param count Count of characters
     * @return The width measured
     */
    private float measureText(char[] src, int index, int count){
        mPaint.setTypeface(mTypefaceText);
        float extraWidth;
        int tabCount = 0;
        for(int i = 0;i < count;i++){
            if(src[index + i] == '\t'){
                tabCount++;
            }
        }
        if(count > 0 && isEmoji(src[index + count - 1])) {
            count--;
            if(count < 0) {
                count = 0;
            }
        }
        extraWidth = mSpaceWidth * (getTabWidth() - 1);
        return mPaint.measureText(src, index, count) + tabCount * extraWidth;
    }

    /**
     * Draw text on the given position
     * @param canvas Canvas to draw
     * @param src Source of characters
     * @param index The index in array
     * @param count Count of characters
     * @param offX Offset x for paint
     * @param offY Offset y for paint(baseline)
     */
    private void drawText(Canvas canvas, char[] src, int index, int count, float offX, float offY){
        int end = index + count;
        int st = index;
        for(int i = index;i < end;i++) {
            if(src[i] == '\t') {
                canvas.drawText(src,st,i - st,offX,offY,mPaint);
                offX = offX + measureText(src,st,i - st + 1);
                st = i + 1;
            }
        }
        if(st < end) {
            canvas.drawText(src,st,end - st,offX,offY,mPaint);
        }
    }

    /**
     * Draw text by spans
     * @param canvas Canvas to draw
     * @param offsetX Start x of text region
     * @param colors Spans
     */
    private void drawTextBySpans(Canvas canvas, float offsetX, TextColorProvider.TextColors colors){
        mPaint.setTypeface(mTypefaceText);
        ColorScheme cs = mColors;
        List<Span> spans = colors.getSpans();
        int first = getFirstVisibleLine();
        int last = getLastVisibleLine();
        int index = 0,copy;
        if(mMinModifiedLine != -1 && mMinModifiedLine <= first) {
            index = binarySearchByIndex(spans,mText.getCharIndex(first,0));
            drawTextBySpansModified(canvas,offsetX,first,last,index,spans);
            return;
        }
        for(int i = first;i <= last;i++){
            index = seekTo(spans, i, index);
            if(mMinModifiedLine != -1 && mMinModifiedLine <= i) {
                drawTextBySpansModified(canvas,offsetX,i,last,binarySearchByIndex(spans,mText.getCharIndex(i,0)),spans);
                break;
            }
            copy = index;
            float off = offsetX;
            prepareLine(i);
            float y = getLineBaseLine(i) - getOffsetY();
            try{
                while(true){
                    Span span = spans.get(copy),next = null;
                    if(copy + 1 < spans.size()){
                        next = spans.get(copy + 1);
                    }
                    int st = span.getLine() == i ? span.getColumn() : 0;
                    int ed = next == null ? mText.getColumnCount(i) : (next.getLine() == i ? next.getColumn() : mText.getColumnCount(i));
                    float width = measureText(mChars, st, ed - st);
                    if(off + width > 0 && off < getWidth()) {
                        mPaint.setColor(cs.getColor(span.colorId));
                        drawText(canvas, mChars, st, ed - st, off, y);
                        if(span.colorId == ColorScheme.HEX_COLOR) {
                            int color = parseColor(st,ed);
                            mRect.bottom = getLineBottom(i) - getOffsetY() - mDpUnit * 1;
                            mRect.top = mRect.bottom - mDpUnit * 4;
                            mRect.left = off;
                            mRect.right = off + width;
                            drawColor(canvas,color,mRect);
                        }
                    }
                    off += width;
                    if(off - offsetX > mMaxPaintX){
                        mMaxPaintX = off - offsetX;
                    }
                    if(next == null || next.getLine() != i){
                        break;
                    }
                    copy++;
                }
            }catch(IndexOutOfBoundsException e){
                drawTextDirect(canvas, offsetX, mColors.getColor(ColorScheme.TEXT_NORMAL), i);
                break;
            }
        }
    }

    /**
     * Parse color string literal to integer
     * @param start Start in mChars
     * @param end End in mChars
     * @return The color parsed.If error,it is zero.
     */
    private int parseColor(int start,int end) {
        char ch = mChars[start];
        if(ch != '"' && ch != '0') {
            return 0;
        }
        boolean type = ch == '0';
        if(type) {
            String v = new String(mChars,start + 2,end - start - 2);
            try{
                //Copied from Color.java
                long color = Long.parseLong(v, 16);
                if (end - start == 8) {
                    // Set the alpha value
                    color |= 0x00000000ff000000;
                }
                return (int)color;
            }catch(RuntimeException e){
                return 0;
            }
        }else{
            if(end - start != 11 && end - start != 9) {
                return 0;
            }
            String v = new String(mChars,start + 1,end - start - 2);
            try{
                return Color.parseColor(v);
            }catch(RuntimeException e){
                return 0;
            }
        }
    }

    /**
     * Get end index of span
     * @param i Index in list
     * @param spans Spans
     * @return End index of span
     */
    private int getSpanEnd(int i,List<Span> spans) {
        return (i + 1 == spans.size() ? mText.length() : spans.get(i + 1).startIndex);
    }

    /**
     * Get end index of line
     * @param i Line
     * @param indexer Indexer to use
     * @return End index of line
     */
    private int getLineEnd(int i,Indexer indexer) {
        return (i + 1 == getLineCount() ? mText.length() + 1 : indexer.getCharIndex(i+1,0));
    }

    /**
     * This method draw text by index in spans instead of line and column.
     * It will be slower than by line but it is used to make the screen a little friendlier
     * @param canvas Canvas to draw
     * @param offsetX Start x of text region
     * @param startLine First visible line
     * @param endLine Last visible line
     * @param index Start index in spans
     * @param spans Spans
     */
    private void drawTextBySpansModified(Canvas canvas, float offsetX , int startLine, int endLine ,int index, List<Span> spans){
        int maxIndex = spans.size() - 1;
        Indexer indexer = getCursor().getIndexer();
        int line = startLine;
        int st = indexer.getCharIndex(line,0);
        int line_st = st;
        while(index <= maxIndex) {
            prepareLine(line);
            float off = offsetX;
            int line_ed = getLineEnd(line,indexer) - 1;
            while(st < line_ed){
                while(index <= maxIndex && getSpanEnd(index,spans) < st) index++;
                int ed = Math.min(getSpanEnd(index,spans),line_ed);
                float advance = off > getWidth() ? 0 : measureText(mChars,st - line_st,ed - st);
                if(off + advance > 0 && off < getWidth()) {
                    mPaint.setColor(mColors.getColor(spans.get(index).colorId));
                    drawText(canvas,mChars,st - line_st,ed - st,off,getLineBaseLine(line) - getOffsetY());
                }
                off += advance;
                st = ed;
                if(ed < line_ed) {
                    index++;
                }else{
                    break;
                }
            }
            line++;
            if(line > endLine) {
                break;
            }
            st = line_st = indexer.getCharIndex(line,0);
        }
    }

    /**
     * Find for the start of line in spans
     * @param spans Spans
     * @param line Searching line
     * @return Start span index in spans
     */
    private int binarySearch(List<Span> spans, int line) {
        int left = 0,right = spans.size() - 1,mid,row;
        int max = right;
        while(left <= right){
            mid = (left + right) / 2;
            if(mid < 0) return 0;
            if(mid > max) return max;
            row = spans.get(mid).getLine();
            if(row == line) {
                return mid;
            }else if(row < line){
                left = mid + 1;
            }else{
                right = mid - 1;
            }
        }
        return Math.max(0,Math.min(left,max));
    }

    /**
     * Find for the start of index in spans
     * @param spans Spans
     * @param index Searching index
     * @return Start span index in spans
     */
    private int binarySearchByIndex(List<Span> spans, int index){
        int left = 0,right = spans.size() - 1,mid,row;
        int max = right;
        while(left <= right){
            mid = (left + right) / 2;
            if(mid < 0) return 0;
            if(mid > max) return max;
            row = spans.get(mid).startIndex;
            if(row == index) {
                return mid;
            }else if(row < index){
                left = mid + 1;
            }else{
                right = mid - 1;
            }
        }
        int result = Math.max(0,Math.min(left,max));
        while(result > 0) {
            if(spans.get(result).startIndex > index) {
                result--;
            }else{
                break;
            }
        }
        while(result < right) {
            if(getSpanEnd(result,spans) < index) {
                result++;
            }else{
                break;
            }
        }
        return result;
    }

    /**
     * Find the first span for line by binary search
     * @param spans Spans
     * @param line Searching line
     * @return First span index
     */
    private int seekTo(List<Span> spans, int line){
        int curr = binarySearch(spans,line);
        if(curr >= spans.size()){
            curr = spans.size() - 1;
        }else if(curr < 0){
            curr = 0;
        }
        while(curr > 0){
            Span span = spans.get(curr);
            if(span.getLine() >= line){
                curr--;
            }else{
                break;
            }
        }
        while(true) {
            Span span = spans.get(curr);
            if(span.getLine() < line && curr + 1 < spans.size() && (spans.get(curr + 1).getLine() < line||(spans.get(curr + 1).getLine() == line && spans.get(curr + 1).getColumn() == 0)) ) {
                curr++;
            }else{
                break;
            }
        }
        return Math.min(curr,spans.size() - 1);
    }

    /**
     * Find first span for the given line
     * @param spans Spans
     * @param line Searching line
     * @param curr Current index
     * @return New position
     */
    private int seekTo(List<Span> spans, int line,int curr){
        if(curr == 0){
            return seekTo(spans,line);
        }
        while(curr < spans.size()) {
            Span span = spans.get(curr);
            if(span.getLine() < line && curr + 1 < spans.size() && (spans.get(curr + 1).getLine() < line||(spans.get(curr + 1).getLine() == line && spans.get(curr + 1).getColumn() == 0))){
                curr++;
            }else{
                break;
            }
        }
        return curr;
    }

    /**
     * Draw text without any spans
     * @param canvas Canvas to draw
     * @param offsetX Start x of text region
     * @param color Color to draw text
     * @param startLine The start line to paint
     */
    private void drawTextDirect(Canvas canvas, float offsetX, int color, int startLine){
        mPaint.setColor(color);
        mPaint.setTypeface(mTypefaceText);
        int last = getLastVisibleLine();
        for(int i = startLine;i <= last;i++){
            if(mText.getColumnCount(i) == 0){
                continue;
            }
            prepareLine(i);
            drawText(canvas, mChars, 0, mText.getColumnCount(i), offsetX, getLineBaseLine(i) - getOffsetY());
            float width = measureText(mChars,0,mText.getColumnCount(i)) + offsetX;
            if(width > mMaxPaintX) {
                mMaxPaintX = width;
            }
        }
    }

    /**
     * Read out characters to mChars for the given line
     * @param line Line going to draw or measure
     */
    private void prepareLine(int line){
        int length = mText.getColumnCount(line);
        if(length >= mChars.length){
            mChars = new char[length + 100];
        }
        for(int i = 0;i < length;i++){
            mChars[i] = mText.charAt(line, i);
        }
    }

    /**
     * Draw background for line
     * @param canvas Canvas to draw
     * @param color Color of background
     * @param line Line index
     */
    private void drawLineBackground(Canvas canvas, int color,int line){
        if(!isLineVisible(line)){
            return;
        }
        mRect.top = getLineTop(line) - getOffsetY();
        mRect.bottom = getLineBottom(line) - getOffsetY();
        mRect.left = 0;
        mRect.right = mViewRect.right;
        drawColor(canvas, color, mRect);
    }

    /**
     * Paint current code block's start and end line's background
     * @param canvas Canvas to draw
     */
    private void drawCurrentCodeBlockLabelBg(Canvas canvas) {
        if(mCursor.isSelected() || !mPaintLabel) {
            return;
        }
        int pos = mCursorPosition;
        if(pos == -1) {
            return;
        }
        List<BlockLine> blocks = mSpanner == null ? null : mSpanner.getColors().getBlocks();
        BlockLine block = (blocks != null && blocks.size() > pos) ? blocks.get(pos) : null;
        if(block != null) {
            int left = mCursor.getLeftLine();
            int color = mColors.getColor(ColorScheme.LINE_BLOCK_LABEL);
            if(block.startLine != left) {
                drawLineBackground(canvas,color,block.startLine);
            }
            if(block.endLine != left) {
                drawLineBackground(canvas,color,block.endLine);
            }
        }
    }

    /**
     * Draw the cursor line's background
     * @param canvas Canvas to draw
     * @param color Color for background
     */
    private void drawCurrentLineBackground(Canvas canvas, int color){
        if(mCursor.isSelected()) {
            return;
        }
        int curr = mCursor.getLeftLine();
        drawLineBackground(canvas,color,curr);
    }

    /**
     * Draw line numbers on screen
     * @param canvas Canvas to draw
     * @param offsetX Start region of line number region
     * @param width The width of line number region
     * @param color Color of line number
     */
    private void drawLineNumbers(Canvas canvas, float offsetX, float width, int color){
        if(width + offsetX <= 0){
            return;
        }
        int first = getFirstVisibleLine();
        int last = getLastVisibleLine();
        mPaint.setTextAlign(mLineNumberAlign);
        mPaint.setColor(color);
        mPaint.setTypeface(mTypefaceLineNumber);
        for(int i = first;i <= last;i++){
            switch(mLineNumberAlign){
                case LEFT:
                    canvas.drawText(Integer.toString(i + 1), offsetX, getLineBaseLine(i) - getOffsetY(), mPaint);
                    break;
                case RIGHT:
                    canvas.drawText(Integer.toString(i + 1), offsetX + width, getLineBaseLine(i) - getOffsetY(), mPaint);
                    break;
                case CENTER:
                    canvas.drawText(Integer.toString(i + 1), offsetX + width / 2f, getLineBaseLine(i) - getOffsetY(), mPaint);
            }
        }
        mPaint.setTextAlign(Paint.Align.LEFT);
    }

    /**
     * Draw line number background
     * @param canvas Canvas to draw
     * @param offsetX Start x of line number region
     * @param width Width of line number region
     * @param color Color of line number background
     */
    private void drawLineNumberBackground(Canvas canvas, float offsetX, float width, int color){
        float right = offsetX + width;
        if(right < 0){
            return;
        }
        float left = Math.max(0f, offsetX);
        mRect.bottom = getHeight();
        mRect.top = 0;
        int offY = getOffsetY();
        if(offY < 0){
            mRect.bottom = mRect.bottom - offY;
            mRect.top = mRect.top - offY;
        }
        mRect.left = left;
        mRect.right = right;
        drawColor(canvas, color, mRect);
    }

    /**
     * Draw divider line
     * @param canvas Canvas to draw
     * @param offsetX End x of line number region
     * @param color Color to draw divider
     */
    private void drawDivider(Canvas canvas, float offsetX, int color){
        float right = offsetX + mDividerWidth;
        if(right < 0){
            return;
        }
        float left = Math.max(0f, offsetX);
        mRect.bottom = getHeight();
        mRect.top = 0;
        int offY = getOffsetY();
        if(offY < 0){
            mRect.bottom = mRect.bottom - offY;
            mRect.top = mRect.top - offY;
        }
        mRect.left = left;
        mRect.right = right;
        drawColor(canvas, color, mRect);
    }

    /**
     * Get the width of line number region
     * @return width of line number region
     */
    private float measureLineNumber(){
        mPaint.setTypeface(mTypefaceLineNumber);
        int count = 0;
        int lineCount = getLineCount();
        while(lineCount > 0){
            count++;
            lineCount /= 10;
        }
        float single = mPaint.measureText("0");
        return single * count * 1.01f;
    }

    /**
     * Draw rect on screen
     * Will not do any thing if color is zero
     * @param canvas Canvas to draw
     * @param color Color of rect
     * @param rect Rect to draw
     */
    private void drawColor(Canvas canvas, int color, RectF rect){
        if(color != 0){
            mPaint.setColor(color);
            canvas.drawRect(rect, mPaint);
        }
    }
    /**
     * Draw rect on screen
     * Will not do any thing if color is zero
     * @param canvas Canvas to draw
     * @param color Color of rect
     * @param rect Rect to draw
     */
    private void drawColor(Canvas canvas, int color, Rect rect){
        if(color != 0){
            mPaint.setColor(color);
            canvas.drawRect(rect, mPaint);
        }
    }

    /**
     * Commit a tab to cursor
     */
    private void commitTab(){
        if(mConnection != null){
            mConnection.commitText("\t", 0);
        }
    }

    /**
     * Update the information of cursor
     * Such as the position of cursor on screen(For input method that can go to any position on screen like PC input method)
     * @return The offset x of right cursor on view
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    protected float updateCursorInfo() {
        CursorAnchorInfo.Builder builder = mAnchorInfoBuilder;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.reset();
            mMatrix.set(getMatrix());
            int[] b = new int[2];
            getLocationOnScreen(b);
            mMatrix.postTranslate(b[0], b[1]);
            builder.setMatrix(mMatrix);
            builder.setSelectionRange(mCursor.getLeft(), mCursor.getRight());
        }
        int l = mCursor.getRightLine();
        int column = mCursor.getRightColumn();
        prepareLine(l);
        boolean visible = true;
        float x = measureLineNumber() + mDividerMargin * 2 + mDividerWidth;
        x = x + measureText(mChars,0,column);
        x = x - getOffsetX();
        if(x < 0) {
            visible = false;
            x = 0;
        }
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setInsertionMarkerLocation(x, getLineTop(l) - getOffsetY(), getLineBaseLine(l) - getOffsetY(), getLineBottom(l) - getOffsetY(), visible ? CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION : CursorAnchorInfo.FLAG_HAS_INVISIBLE_REGION);
            mInputMethodManager.updateCursorAnchorInfo(this, builder.build());
        }
        return x;
    }

    /**
     * Make the right line visible
     */
    public void makeRightVisible() {
        makeCharVisible(getCursor().getRightLine(),getCursor().getRightColumn());
    }

    /**
     * Make the given character position visible
     * @param line Line of char
     * @param column Column of char
     */
    public void makeCharVisible(int line,int column) {
        float y = getLineHeight() * line;
        float minY = getOffsetY();
        float maxY = minY + getHeight();
        float targetY = minY;
        if(y < minY) {
            targetY = y;
        }else if(y + getLineHeight() > maxY) {
            targetY = y + getLineHeight() - getHeight();
        }
        float prefix_width = measureLineNumber() + mDividerMargin * 2 + mDividerWidth;
        float minX = getOffsetX();
        float maxX = minX + getWidth();
        float targetX = minX;
        prepareLine(line);
        float x = prefix_width + measureText(mChars,0,column);
        float char_width = 2 * measureText(mChars,column,1);
        if(x < minX) {
            targetX = x;
        }else if(x + char_width > maxX) {
            targetX = x + char_width - getWidth();
        }
        if(targetY == minY && targetX == minX) {
            invalidate();
            return;
        }
        boolean animation = true;
        if(System.currentTimeMillis() - mLastMakeVisible < 100) {
            animation = false;
        }
        mLastMakeVisible = System.currentTimeMillis();
        if(animation){
            getScroller().forceFinished(true);
            getScroller().startScroll(getOffsetX(),getOffsetY(),(int)(targetX - getOffsetX()),(int)(targetY - getOffsetY()));
            if(Math.abs(getOffsetY() - targetY) > mDpUnit * 100) {
                mEventHandler.notifyScrolled();
            }
        }else{
            getScroller().startScroll(getOffsetX(),getOffsetY(),(int)(targetX - getOffsetX()),(int)(targetY - getOffsetY()),0);
        }

        invalidate();
    }

    /**
     * Whether there is clip
     * @return whether clip in clip board
     */
    public boolean hasClip() {
        return (mClipboardManager.getTextFromClipboard() != null);
    }

    /**
     * Get 1dp = ?px
     * @return 1dp in pixel
     */
    protected float getDpUnit() {
        return mDpUnit;
    }

    /**
     * Get scroller from EventHandler
     * You would better not use it for your own scrolling
     * @return The scroller
     */
    public OverScroller getScroller() {
        return mEventHandler.getScroller();
    }

    /**
     * Whether the position is over max Y position
     * @param posOnScreen Y position on view
     * @return Whether over max Y
     */
    public boolean isOverMaxY(float posOnScreen) {
        return getPointLine(posOnScreen + getOffsetY()) > getLineCount();
    }

    /**
     * Whether the position is over max X position
     * @param posOnScreen X position on view
     * @return Whether over max X
     */
    public boolean isOverMaxX(int line,float posOnScreen) {
        float xx = posOnScreen + getOffsetX() - mDividerMargin * 2 - mDividerWidth - measureLineNumber();
        prepareLine(line);
        return xx > (measureText(mChars,0,mText.getColumnCount(line)) + 2 * mDpUnit);
    }

    /**
     * Get y position in scroll bounds's line's line
     * @param yPos Y in scroll bounds
     * @return line
     */
    public int getPointLine(float yPos){
        int r = (int)yPos / getLineHeight();
        return r < 0 ? 0 : r;
    }

    /**
     * Get Y position on screen's line
     * @param y Y on screen
     * @return Line
     */
    public int getPointLineOnScreen(float y){
        return Math.min(getPointLine(y + getOffsetY()),getLineCount() - 1);
    }

    /**
     * Get column in line for offset x
     * @param line Line
     * @param x Offset x
     * @return Column in line
     */
    public int getPointColumn(int line, float x){
        if(x < 0){
            return 0;
        }
        if(line >= getLineCount()) {
            line = getLineCount() - 1;
        }
        float w = 0;
        int i = 0;
        prepareLine(line);
        while(w < x && i < mText.getColumnCount(line)){
            w += measureText(mChars, i, 1);
            i++;
        }
        if(w < x){
            i = mText.getColumnCount(line);
        }
        return i;
    }

    /**
     * Get column for x offset on screen
     * @param line Line
     * @param x X offset on screen
     * @return Column in line
     */
    public int getPointColumnOnScreen(int line, float x){
        float xx = x + getOffsetX() - mDividerMargin * 2 - mDividerWidth - measureLineNumber();
        return getPointColumn(line, xx);
    }

    /**
     * Get max scroll y
     * @return max scrol y
     */
    public int getScrollMaxY(){
        return Math.max(0, getLineHeight() * getLineCount() - getHeight() / 2);
    }

    /**
     * Get max scroll x
     * @return max scroll x
     */
    public int getScrollMaxX(){
        return (int)Math.max(0, mMaxPaintX - getWidth() / 2f);
    }

    /**
     * Set tab width
     * @param w tab width compared to space
     */
    public void setTabWidth(int w){
        if(w < 1){
            throw new IllegalArgumentException("width can not be under 1");
        }
        mTabWidth = w;
        if(mCursor != null) {
            mCursor.setTabWidth(mTabWidth);
        }
    }

    /**
     * Format text
     */
    public void formatCode() {
        StringBuilder content = mText.toStringBuilder();
        mCancelForFormatting = true;
        mText.beginBatchEdit();
        int line = mCursor.getLeftLine();
        int column = mCursor.getLeftColumn();
        mText.delete(0,0,getLineCount() - 1,mText.getColumnCount(getLineCount() - 1));
        mText.insert(0,0,mLanguage.format(content));
        mText.endBatchEdit();
        getScroller().forceFinished(true);
        mACPanel.hide();
        mCancelForFormatting = false;
        setSelection(line,column);
    }

    /**
     * Get tab width
     * @return tab width
     */
    public int getTabWidth(){
        return mTabWidth;
    }

    /**
     * Undo last action
     */
    public void undo(){
        mText.undo();
    }

    /**
     * Redo last action
     */
    public void redo(){
        mText.redo();
    }

    /**
     * Whether can undo
     * @return whether can undo
     */
    public boolean canUndo(){
        return mText.canUndo();
    }

    /**
     * Whether can redo
     * @return whether can redo
     */
    public boolean canRedo(){
        return mText.canRedo();
    }

    /**
     * Enable / disabled undo manager
     * @param enabled Enable/Disable
     */
    public void setUndoEnabled(boolean enabled){
        mUndoEnabled = enabled;
        if(mText != null){
            mText.setUndoEnabled(enabled);
        }
    }

    /**
     * @see RoseEditor#setUndoEnabled(boolean)
     * @return Enabled/Disabled
     */
    public boolean isUndoEnabled(){
        return mUndoEnabled;
    }

    /**
     * Set whether drag mode
     * drag:no fling
     * @param drag Whether drag
     */
    public void setDrag(boolean drag){
        mDrag = drag;
        if(drag && !mEventHandler.getScroller().isFinished()){
            mEventHandler.getScroller().forceFinished(true);
        }
    }

    /**
     * @see RoseEditor#setDrag(boolean)
     * @return Whether drag
     */
    public boolean isDrag(){
        return mDrag;
    }

    /**
     * Set divider line's left and right margin
     * @param dividerMargin Margin for divider line
     */
    public void setDividerMargin(float dividerMargin) {
        if(dividerMargin < 0){
            throw new IllegalArgumentException("margin can not be under zero");
        }
        this.mDividerMargin = dividerMargin;
        invalidate();
    }

    /**
     * @see RoseEditor#setDividerMargin(float)
     * @return Margin of divider line
     */
    public float getDividerMargin() {
        return mDividerMargin;
    }

    /**
     * Set divider line's width
     * @param dividerWidth Width of divider line
     */
    public void setDividerWidth(float dividerWidth) {
        if(dividerWidth < 0){
            throw new IllegalArgumentException("width can not be under zero");
        }
        this.mDividerWidth = dividerWidth;
        invalidate();
    }

    /**
     * @see RoseEditor#setDividerWidth(float)
     * @return Width of divider line
     */
    public float getDividerWidth() {
        return mDividerWidth;
    }

    /**
     * Set line number's typeface
     * @param typefaceLineNumber New typeface
     */
    public void setTypefaceLineNumber(Typeface typefaceLineNumber) {
        if(typefaceLineNumber == null){
            typefaceLineNumber = Typeface.MONOSPACE;
        }
        this.mTypefaceLineNumber = typefaceLineNumber;
        invalidate();
    }

    /**
     * Set text's typeface
     * @param typefaceText New typeface
     */
    public void setTypefaceText(Typeface typefaceText) {
        if(typefaceText == null){
            typefaceText = Typeface.MONOSPACE;
        }
        this.mTypefaceText = typefaceText;
        invalidate();
    }

    /**
     * @see RoseEditor#setTypefaceLineNumber(Typeface)
     * @return Typeface of line number
     */
    public Typeface getTypefaceLineNumber() {
        return mTypefaceLineNumber;
    }

    /**
     * @see RoseEditor#setTypefaceText(Typeface)
     * @return Typeface of text
     */
    public Typeface getTypefaceText() {
        return mTypefaceText;
    }

    /**
     * Set line number align
     * @param align Align for line number
     */
    public void setLineNumberAlign(Paint.Align align){
        if(align == null){
            align = Paint.Align.LEFT;
        }
        mLineNumberAlign = align;
        invalidate();
    }

    /**
     * @see RoseEditor#setLineNumberAlign(Paint.Align)
     * @return Line number align
     */
    public Paint.Align getLineNumberAlign(){
        return mLineNumberAlign;
    }

    /**
     * Widht for insert cursor
     * @param width Cursor width
     */
    public void setCursorWidth(float width){
        if(width < 0){
            throw new IllegalArgumentException("width can not be under zero");
        }
        mInsertSelWidth = width;
        invalidate();
    }

    /**
     * Get Cursor
     * Internal method!
     * @return Cursor of text
     */
    public Cursor getCursor(){
        return mCursor;
    }

    /**
     * Display soft input method for self
     */
    public void showSoftInput(){
        if(isEditable() && isEnabled()){
            if(isInTouchMode()){
                requestFocusFromTouch();
            }
            if(!hasFocus()){
                requestFocus();
            }
            mInputMethodManager.showSoftInput(this, 0);
        }
    }

    /**
     * Hide soft input
     */
    public void hideSoftInput(){
        mInputMethodManager.hideSoftInputFromWindow(getWindowToken(), 0);
    }

    /**
     * Get line count
     * @return line count
     */
    public int getLineCount(){
        return mText.getLineCount();
    }

    /**
     * Get first visible line on screen
     * @return first visible line
     */
    public int getFirstVisibleLine(){
        int j = Math.min(getOffsetY() / getLineHeight(), getLineCount() - 1);
        if(j < 0){
            return 0;
        }else{
            return j;
        }
    }

    /**
     * Get last visible line on screen
     * @return last visible line
     */
    public int getLastVisibleLine(){
        int l = Math.min((getOffsetY() + getHeight()) / getLineHeight(), getLineCount() - 1);
        if(l < 0){
            return 0;
        }else{
            return l;
        }
    }

    /**
     * Whether this line is visible on screen
     * @param line Line to check
     * @return Whether visible
     */
    public boolean isLineVisible(int line){
        return (getFirstVisibleLine() <= line && line <= getLastVisibleLine());
    }

    /**
     * Get baseline directly
     * @param line Line
     * @return baseline y offset
     */
    public int getLineBaseLine(int line){
        return getLineHeight() * (line + 1) - mPaint.getFontMetricsInt().descent;
    }

    /**
     * Get line height
     * @return height of single line
     */
    public int getLineHeight(){
        return mPaint.getFontMetricsInt().descent - mPaint.getFontMetricsInt().ascent;
    }

    /**
     * Get line top y offset
     * @param line Line
     * @return top y offset
     */
    public int getLineTop(int line){
        return getLineHeight() * line;
    }

    /**
     * Get line bottom y offset
     * @param line Line
     * @return Bottom y offset
     */
    public int getLineBottom(int line){
        return getLineHeight() * (line + 1);
    }

    /**
     * Get scroll x
     * @return scroll x
     */
    public int getOffsetX(){
        return mEventHandler.getScroller().getCurrX();
    }

    /**
     * Get scroll y
     * @return scroll y
     */
    public int getOffsetY(){
        return mEventHandler.getScroller().getCurrY();
    }

    /**
     * Set whether text can be edited
     * @param editable Editable
     */
    public void setEditable(boolean editable){
        mEditable = editable;
        if(!editable){
            hideSoftInput();
        }
    }

    /**
     * @see RoseEditor#setEditable(boolean)
     * @return Whether editable
     */
    public boolean isEditable(){
        return mEditable;
    }

    /**
     * Allow scale text size by thumb
     * @param scale Whether allow
     */
    public void setCanScale(boolean scale){
        mScale = scale;
    }

    /**
     * @see RoseEditor#setCanScale(boolean)
     * @return Whether allow to scale
     */
    public boolean canScale(){
        return mScale;
    }

    /**
     * Move the selection down
     * If the auto complete panel is shown,move the selection in panel to next
     */
    public void moveSelectionDown(){
        if(mACPanel.isShowing()) {
            mACPanel.moveDown();
            return;
        }
        Cursor c = getCursor();
        int line = c.getLeftLine();
        int column = c.getLeftColumn();
        int c_line = getText().getLineCount();
        if(line + 1 >= c_line){
            setSelection(line, getText().getColumnCount(line));
        }else{
            int c_column = getText().getColumnCount(line + 1);
            if(column > c_column){
                column = c_column;
            }
            setSelection(line + 1, column);
        }
    }

    /**
     * Move the selection up
     * If Auto complete panel is shown,move the selection in panel to last
     */
    public void moveSelectionUp(){
        if(mACPanel.isShowing()) {
            mACPanel.moveUp();
            return;
        }
        Cursor c = getCursor();
        int line = c.getLeftLine();
        int column = c.getLeftColumn();
        if(line - 1 < 0){
            line = 1;
        }
        int c_column = getText().getColumnCount(line - 1);
        if(column > c_column){
            column = c_column;
        }
        setSelection(line - 1, column);
    }

    /**
     * Move the selection left
     */
    public void moveSelectionLeft(){
        Cursor c = getCursor();
        int line = c.getLeftLine();
        int column = c.getLeftColumn();
        if(column - 1 >= 0){
            int toLeft = 1;
            if(column - 2 >= 0) {
                char ch = mText.charAt(line,column - 2);
                if(isEmoji(ch)) {
                    column--;
                    toLeft++;
                }
            }
            setSelection(line, column - 1);
            if(mACPanel.isShowing()) {
                String prefix = mACPanel.getPrefix();
                if(prefix.length() > toLeft) {
                    prefix = prefix.substring(0,prefix.length() - toLeft);
                    mACPanel.setPrefix(prefix);
                }else{
                    mACPanel.hide();
                }
            }
            if(column - 1 <= 0) {
                mACPanel.hide();
            }
        }else{
            if(line == 0){
                setSelection(0, 0);
            }else{
                int c_column = getText().getColumnCount(line - 1);
                setSelection(line - 1, c_column);
            }
        }
    }

    /**
     * Move the selection right
     */
    public void moveSelectionRight(){
        Cursor c = getCursor();
        int line = c.getLeftLine();
        int column = c.getLeftColumn();
        int c_column = getText().getColumnCount(line);
        if(column + 1 <= c_column){
            char ch = mText.charAt(line,column);
            boolean emoji;
            if(emoji = isEmoji(ch)) {
                column ++;
                if(column + 1 > c_column){
                    column--;
                }
            }
            if(!emoji && mACPanel.isShowing()) {
                if(!mLanguage.isAutoCompleteChar(ch)) {
                    mACPanel.hide();
                }else{
                    String prefix = mACPanel.getPrefix() + ch;
                    mACPanel.setPrefix(prefix);
                }
            }
            setSelection(line, column + 1);
        }else{
            if(line + 1 == getLineCount()){
                setSelection(line, c_column);
            }else{
                setSelection(line + 1, 0);
            }
        }
    }

    /**
     * Move selection to end of line
     */
    public void moveSelectionEnd(){
        int line = mCursor.getLeftLine();
        setSelection(line, getText().getColumnCount(line));
    }

    /**
     * Move selection to start of line
     */
    public void moveSelectionHome(){
        setSelection(mCursor.getLeftLine(), 0);
    }

    /**
     * Move selection to given position
     * @param line The line to move
     * @param column The column to move
     */
    public void setSelection(int line, int column){
        if(column > 0 && isEmoji(mText.charAt(line,column - 1))) {
            column++;
            if(column > mText.getColumnCount(line)) {
                column--;
            }
        }
        mCursor.set(line, column);
        if(mHighlightCurrentBlock){
            mCursorPosition = findCursorBlock();
        }
        updateCursorInfo();
        makeCharVisible(line,column);
        mTextActionPanel.hide();
    }

    /**
     * Select all text
     */
    public void selectAll(){
        setSelectionRegion(0,0,getLineCount() - 1,getText().getColumnCount(getLineCount() - 1));
    }

    /**
     * Set selection region with a call to {@link RoseEditor#makeRightVisible()}
     * @param lineLeft Line left
     * @param columnLeft Column Left
     * @param lineRight Line right
     * @param columnRight Column right
     */
    public void setSelectionRegion(int lineLeft, int columnLeft, int lineRight, int columnRight) {
        setSelectionRegion(lineLeft,columnLeft,lineRight,columnRight,true);
    }

    /**
     * Set selection region
     * @param lineLeft Line left
     * @param columnLeft Column Left
     * @param lineRight Line right
     * @param columnRight Column right
     * @param makeRightVisible Whether make right cursor visible
     */
    public void setSelectionRegion(int lineLeft, int columnLeft, int lineRight, int columnRight,boolean makeRightVisible){
        int start = getText().getCharIndex(lineLeft, columnLeft);
        int end = getText().getCharIndex(lineRight, columnRight);
        if(start == end) {
            setSelection(lineLeft,columnLeft);
            return;
        }
        if(start > end){
            throw new IllegalArgumentException("start > end");
        }
        if(columnLeft > 0) {
            int column = columnLeft - 1;
            char ch = mText.charAt(lineLeft,column);
            if(isEmoji(ch)) {
                columnLeft++;
                if(columnLeft > mText.getColumnCount(lineLeft)) {
                    columnLeft--;
                }
            }
        }
        if(columnRight > 0) {
            int column = columnRight;
            char ch = mText.charAt(lineRight,column);
            if(isEmoji(ch)) {
                columnRight++;
                if(columnRight > mText.getColumnCount(lineRight)) {
                    columnRight--;
                }
            }
        }
        mCursor.setLeft(lineLeft, columnLeft);
        mCursor.setRight(lineRight, columnRight);
        updateCursorInfo();
        if(makeRightVisible)
            makeCharVisible(lineRight,columnRight);
        else
            invalidate();
    }

    /**
     * Move to next page
     */
    public void movePageDown(){
        mEventHandler.onScroll(null, null, 0, getHeight());
        mACPanel.hide();
    }

    /**
     * Move to previous page
     */
    public void movePageUp(){
        mEventHandler.onScroll(null, null, 0, -getHeight());
        mACPanel.hide();
    }

    /**
     * Paste text from clip board
     */
    public void pasteText(){
        try{
            CharSequence text = mClipboardManager.getTextFromClipboard();
            if(text != null && mConnection != null){
                mConnection.commitText(text, 0);
            }
        }catch(Exception e){
            e.printStackTrace();
            Toast.makeText(getContext(),e.toString(),Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Copy text to clip board
     */
    public void copyText(){
        try{
            if(mCursor.isSelected()){
                mClipboardManager.setTextToClipboard(getText().subContent(mCursor.getLeftLine(),
                        mCursor.getLeftColumn(),
                        mCursor.getRightLine(),
                        mCursor.getRightColumn()).toString());
            }
        }catch(Exception e){
            e.printStackTrace();
            Toast.makeText(getContext(),e.toString(),Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Set the editor's text displaying
     * @param text the new text you want to display
     */
    public void setText(CharSequence text){
        if(text == null){
            text = "";
        }

        if(mText != null){
            mText.removeContentListener(this);
        }
        mText = new Content(text);
        mCursor = mText.getCursor();
        mCursor.setAutoIndent(mAutoIndent);
        mCursor.setLanguage(mLanguage);
        mEventHandler.reset();
        mText.addContentListener(this);
        mText.setUndoEnabled(mUndoEnabled);

        if(mSpanner != null){
            mSpanner.setCallback(null);
        }
        mSpanner = new TextColorProvider(mLanguage.createAnalyzer());
        mSpanner.setCallback(this);

        TextColorProvider.TextColors colors = mSpanner.getColors();
        colors.getSpans().clear();
        mSpanner.analyze(getText());

        mMaxPaintX = 0;
        mMinModifiedLine = -1;
        //requestLayout();

        if(mInputMethodManager != null) {
            mInputMethodManager.restartInput(this);
        }
        invalidate();
    }

    /**
     * @see RoseEditor#setText(CharSequence)
     * @return Text displaying
     */
    public Content getText(){
        return mText;
    }

    /**
     * Set the editor's text size in sp unit. This value must be > 0
     *
     * @param textSize the editor's text size in <strong>Sp</strong> units.
     */
    public void setTextSize(float textSize){
        Context context = getContext();
        Resources res;

        if(context == null){
            res = Resources.getSystem();
        }else{
            res = context.getResources();
        }

        setTextSizePx(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSize, res.getDisplayMetrics()));
    }

    /**
     * Get the paint of the editor
     * Users should not change text size and other arguments that are related to text measure by the object
     *
     * @return The paint which is used by the editor now
     */
    public Paint getPaint() {
        return mPaint;
    }

    /**
     * Get the ColorScheme object of this editor
     * You can config colors of some regions, texts and highlight text
     *
     * @return ColorScheme object using
     */
    public ColorScheme getColorScheme(){
        return mColors;
    }

    /**
     * Move selection to line start with scrolling
     * @param line Line to jump
     */
    public void jumpToLine(int line) {
        setSelection(line,0);
    }

    /**
     * Get spans
     * @return spans
     */
    public TextColorProvider.TextColors getTextColor(){
        return mSpanner.getColors();
    }

    /**
     * Hide auto complete panel if shown
     */
    public void hideAutoCompletePanel() {
        mACPanel.hide();
    }

    /**
     * Get cursor code block index
     * @return index of cursor's code block
     */
    public int getBlockIndex() {
        return mCursorPosition;
    }

    /**
     * Move up a length of line
     */
    private void moveLineOn() {
        getScroller().startScroll(getOffsetX(),getOffsetY(),0,getLineHeight(),0);
    }

    //------------------------Internal Callbacks------------------------------

    /**
     * Called by ColorScheme to notify invalidate
     * @param type Color type changed
     */
    protected void onColorUpdated(int type){
        if(type == ColorScheme.AUTO_COMP_PANEL_BG || type == ColorScheme.AUTO_COMP_PANEL_CORNER) {
            if(mACPanel != null)
                mACPanel.applyColor();
            return;
        }
        invalidate();
    }

    /**
     * Called by RoseEditorInputConnection
     */
    protected void onCloseConnection(){

    }

    //------------------------Overrides---------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        try{
            drawView(canvas);
        }catch(Throwable t){
            StringBuilder sb = mErrorBuilder;
            sb.setLength(0);
            sb.append(t.toString());
            for(Object o : t.getStackTrace()){
                sb.append('\n').append(o);
            }
            Toast.makeText(getContext(), sb, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void invalidate() {
        if(mCancelForFormatting) {
            return;
        }
        super.invalidate();
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return isEnabled() && isEditable();
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        if(!isEditable() || !isEnabled()){
            return null;
        }
        outAttrs.inputType = EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE;
        mConnection.reset();
        return mConnection;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean res = mEventHandler.onTouchEvent(event);
        boolean res2 = false;
        boolean res3 = mScaleDetector.onTouchEvent(event);
        if(!mEventHandler.handlingMotions()) {
            res2 = mBasicDetector.onTouchEvent(event);
        }
        return (res3 || res2 || res);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if(event.getAction() == KeyEvent.ACTION_DOWN){
            switch(keyCode){
                case KeyEvent.KEYCODE_DEL:
                case KeyEvent.KEYCODE_FORWARD_DEL:
                    if(mConnection != null)
                        return mConnection.deleteSurroundingText(0, 0);
                    return true;
                case KeyEvent.KEYCODE_ENTER:
                    if(mACPanel.isShowing()) {
                        mACPanel.select();
                        return true;
                    }
                    return mConnection.commitText("\n", 0);
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    moveSelectionDown();
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    moveSelectionUp();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    moveSelectionLeft();
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    moveSelectionRight();
                    return true;
                case KeyEvent.KEYCODE_MOVE_END:
                    moveSelectionEnd();
                    return true;
                case KeyEvent.KEYCODE_MOVE_HOME:
                    moveSelectionHome();
                    return true;
                case KeyEvent.KEYCODE_PAGE_DOWN:
                    movePageDown();
                    return true;
                case KeyEvent.KEYCODE_PAGE_UP:
                    movePageUp();
                    return true;
                case KeyEvent.KEYCODE_TAB:
                    commitTab();
                    return true;
                case KeyEvent.KEYCODE_PASTE:
                    pasteText();
                    return true;
                case KeyEvent.KEYCODE_COPY:
                    copyText();
                    return true;
                case KeyEvent.KEYCODE_SPACE:
                    getCursor().onCommitText(" ");
                    return true;
                default:
                    if(event.isCtrlPressed() && !event.isAltPressed()){
                        switch(keyCode){
                            case KeyEvent.KEYCODE_V:
                                pasteText();
                                return true;
                            case KeyEvent.KEYCODE_C:
                                copyText();
                                return true;
                            case KeyEvent.KEYCODE_X:
                                copyText();
                                if(mCursor.isSelected()){
                                    mCursor.onDeleteKeyPressed();
                                }
                                return true;
                            case KeyEvent.KEYCODE_A:
                                selectAll();
                                return true;
                            case KeyEvent.KEYCODE_Z:
                                undo();
                                return true;
                            case KeyEvent.KEYCODE_Y:
                                redo();
                                return true;
                        }
                    }else if(!event.isCtrlPressed() && !event.isAltPressed()) {
                        char[] c = new char[1];
                        if(event.isPrintingKey()) {
                            c[0] = (char) event.getUnicodeChar(event.getMetaState());
                        }else{
                            return super.onKeyDown(keyCode, event);
                        }
                        getCursor().onCommitText(new String(c));
                        return true;
                    }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        boolean warn = false;
        //Fill the horizontal layout if WRAP_CONTENT mode
        if(MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.AT_MOST || MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED){
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY);
            warn = true;
        }
        //Fill the vertical layout if WRAP_CONTENT mode
        if(MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST || MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED){
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY);
            warn = true;
        }
        if(warn){
            Log.i(LOG_TAG, "onMeasure():Rose editor does not support wrap_content mode when measuring.It will just fill the whole space.");
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event)
    {
        if(event.getAction() == MotionEvent.ACTION_SCROLL){
            float v_scroll = -event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            if(v_scroll != 0) {
                mEventHandler.onScroll(event, event, 0, v_scroll * 20);
            }
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldWidth, int oldHeight) {
        super.onSizeChanged(w, h, oldWidth, oldHeight);
        mViewRect.right = w;
        mViewRect.bottom = h;
    }

    @Override
    public void computeScroll() {
        if(mEventHandler.getScroller().computeScrollOffset()){
            invalidate();
        }
        super.computeScroll();
    }

    @Override
    public void beforeReplace(Content content) {
        mWait = true;
    }
    //There is problems in this
    //I will fix it soon
	/*
	private void shiftSpansOnInsert(int line,int startColumn,int length) {
		List<TextColorProvider.Span> spans = mSpanner.getColors().getSpans();
		int spanStart = binarySearch(spans,line);
		TextColorProvider.Span span;
		spanStart = Math.max(0,spanStart - 2);
		for(int i = spanStart;i < spans.size();i++) {
			span = spans.get(i);
			if(span.line == line && span.column >= startColumn) {
				span.column += length;
			}else if(span.line > line) {
				break;
			}
		}
	}*/

    @Override
    public void afterInsert(Content content, int startLine, int startColumn, int endLine, int endColumn, CharSequence insertedContent) {
        //if(startLine == endLine) {
        //shiftSpansOnInsert(startLine,startColumn,endColumn - startColumn);
        //}else{
        mMinModifiedLine = mMinModifiedLine == -1 ? startLine : Math.min(startLine,mMinModifiedLine);
        //}
        mWait = false;
        if(endColumn == 0 || startLine != endLine) {
            mACPanel.hide();
            makeRightVisible();
            mSpanner.analyze(mText);
            mEventHandler.hideInsertHandle();
            return;
        }else{
            int end = endColumn;
            while(endColumn > 0) {
                if(mLanguage.isAutoCompleteChar(content.charAt(endLine,endColumn - 1))) {
                    endColumn--;
                }else{
                    break;
                }
            }
            String line = content.getLineString(endLine);
            if(end == endColumn) {
                mACPanel.hide();
                makeRightVisible();
                mSpanner.analyze(mText);
                mEventHandler.hideInsertHandle();
                return;
            }
            String prefix = line.substring(endColumn,end);
            mACPanel.setPrefix(prefix);
        }
        float panelX = updateCursorInfo() + mDpUnit * 20;
        float panelY = getLineBottom(mCursor.getRightLine()) - getOffsetY() + getLineHeight() / 2;
        float restY = getHeight() - panelY;
        if(restY > mDpUnit * 200) {
            restY = mDpUnit * 200;
        }else if(restY < mDpUnit * 100) {
            int first = getFirstVisibleLine();
            while(restY < mDpUnit * 100) {
                restY += getLineHeight();
                panelY -= getLineHeight();
                first++;
                moveLineOn();
            }
            if(mACPanel.isShowing()) {
                mACPanel.hide();
            }
        }
        if(mACPanel.getY() != panelY) {
            mACPanel.hide();
        }
        mACPanel.setExtendedX(panelX);
        mACPanel.setExtendedY(panelY);
        if(getWidth() < 500 * mDpUnit) {
            //Open center mode
            mACPanel.setWidth(getWidth() * 7 / 8);
            mACPanel.setExtendedX(getWidth() / 8f / 2f);
        }else{
            mACPanel.setWidth(getWidth() / 2);
        }
        mACPanel.setHeight((int)restY);
            mACPanel.show();
            makeRightVisible();
            mSpanner.analyze(mText);
            mEventHandler.hideInsertHandle();


    }

    @Override
    public void afterDelete(Content content, int startLine, int startColumn, int endLine, int endColumn, CharSequence deletedContent) {
        mMinModifiedLine = mMinModifiedLine == -1 ? startLine : Math.min(startLine,mMinModifiedLine);
        if(mConnection.composingLine == -1 && mACPanel.isShowing()) {
            if(startLine != endLine || startColumn != endColumn - 1) {
                mACPanel.hide();
            }
            String prefix = mACPanel.getPrefix();
            if(prefix == null || prefix.length() - 1 == 0) {
                mACPanel.hide();
            }else{
                prefix = prefix.substring(0,prefix.length() - 1);
                mACPanel.setPrefix(prefix);

            }
        }
        if(!mWait){
            updateCursorInfo();
            makeRightVisible();
            mSpanner.analyze(mText);
            mEventHandler.hideInsertHandle();
        }
    }

    @Override
    public void onAnalysisDone(TextColorProvider provider, TextColorProvider.TextColors colors) {
        if(mHighlightCurrentBlock){
            mCursorPosition = findCursorBlock();
        }
        postInvalidate();
        mMinModifiedLine = -1;
        m = System.currentTimeMillis() - provider.st;
    }

}
