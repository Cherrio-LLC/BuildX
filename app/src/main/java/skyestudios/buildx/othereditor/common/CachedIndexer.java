package skyestudios.buildx.othereditor.common;

import java.util.ArrayList;
import java.util.List;

import skyestudios.buildx.othereditor.interfaces.ContentListener;
import skyestudios.buildx.othereditor.interfaces.Indexer;
import skyestudios.buildx.othereditor.simpleclass.CharPosition;


/**
 * Indexer Impl for Content
 * With cache
 * @author Rose
 */
class CachedIndexer implements Indexer, ContentListener {

    private Content _c;
    private CharPosition _zero = new CharPosition().zero();
    private CharPosition _end = new CharPosition();
    private List<CharPosition> _cache = new ArrayList<>();
    private int _switchIndex = 50;
    private int _switchLine = 50;
    private int _maxSize = 50;
    private boolean _handle = true;
    private boolean _throw = false;
    private boolean _lex = false;
    private CharPosition _the = new CharPosition().zero();

    /**
     * If the querying index is larger than the switch
     * We will add its result to cache
     * @param s Switch
     */
    public void setSwitchIndex(int s){
        _switchIndex = s;
    }

    /**
     * Enabled lex mode to make it quicker
     * @deprecated New way to quicken has been created
     */
    @Deprecated
    public void setLex() {
        _lex = true;
        _maxSize = 1;
        push(_the);
        _end.zero();
    }

    /**
     * Create a new CachedIndexer for the given content
     * @param content Content to manage
     */
    /*package*/ CachedIndexer(Content content) {
        _c = content;
        _detectException();
    }

    /**
     * Find out whether things unexpected happened
     */
    private void _detectException() {
        if(!isHandleEvent() && !_cache.isEmpty()) {
            _throw = true;
        }
        _end.index = _c.length();
        _end.line = _c.getLineCount() - 1;
        _end.column = _c.getColumnCount(_end.line);
    }

    /**
     * Throw a new exception for illegal state
     */
    protected void _throw() {
        if(_throw) {
            throw new IllegalStateException("there is cache but the content changed");
        }
    }

    /**
     * Get nearest cache for the given index
     * @param index Querying index
     * @return Nearest cache
     */
    private CharPosition findNearestByIndex(int index) {
        int min = index,dis = index;
        CharPosition nearestCharPosition = _zero;
        for(CharPosition pos : _cache) {
            dis = Math.abs(pos.index - index);
            if(dis < min) {
                min = dis;
                nearestCharPosition = pos;
            }
        }
        if(Math.abs(_end.index - index) < dis) {
            nearestCharPosition = _end;
        }
        if(nearestCharPosition != _zero && nearestCharPosition != _end) {
            _cache.remove(nearestCharPosition);
            _cache.add(nearestCharPosition);
        }
        return nearestCharPosition;
    }

    /**
     * Get nearest cache for the given line
     * @param line Querying line
     * @return Nearest cache
     */
    private CharPosition findNearestByLine(int line) {
        int min = line,dis = line;
        CharPosition nearestCharPosition = _zero;
        for(CharPosition pos : _cache) {
            dis = Math.abs(pos.line - line);
            if(dis < min) {
                min = dis;
                nearestCharPosition = pos;
            }
        }
        if(Math.abs(_end.line - line) < dis) {
            nearestCharPosition = _end;
        }
        if(nearestCharPosition != _zero && nearestCharPosition != _end) {
            _cache.remove(nearestCharPosition);
            _cache.add(nearestCharPosition);
        }
        return nearestCharPosition;
    }

    /**
     * From the given position to find forward in text
     * @param start Given position
     * @param index Querying index
     * @return The querying position
     */
    private CharPosition findIndexForward(CharPosition start,int index) {
        if(start.index > index) {
            throw new IllegalArgumentException("Unable to find backward from method findIndexForward()");
        }
        int workLine = start.line;
        int workColumn = start.column;
        int workIndex = start.index;
        //Move the column to the line end
        {
            int column = _c.getColumnCount(workLine);
            workIndex += column - workColumn;
            workColumn = column;
        }
        while(workIndex < index) {
            workLine++;
            workColumn = _c.getColumnCount(workLine);
            workIndex += workColumn + 1;
        }
        if(workIndex > index) {
            workColumn -= workIndex - index;
        }
        CharPosition pos = _lex ? _the : new CharPosition();
        pos.column = workColumn;
        pos.line = workLine;
        pos.index = index;
        return pos;
    }

    /**
     * From the given position to find backward in text
     * @param start Given position
     * @param index Querying index
     * @return The querying position
     */
    private CharPosition findIndexBackward(CharPosition start,int index) {
        if(start.index < index) {
            throw new IllegalArgumentException("Unable to find forward from method findIndexBackward()");
        }
        int workLine = start.line;
        int workColumn = start.column;
        int workIndex = start.index;
        while(workIndex > index) {
            workIndex -= workColumn + 1;
            workLine--;
            if(workLine != -1) {
                workColumn = _c.getColumnCount(workLine);
            }else {
                //Reached the start of text,we have to use findIndexForward() as this method can not handle it
                return findIndexForward(_zero,index);
            }
        }
        int dColumn = index - workIndex;
        if(dColumn > 0) {
            workLine++;
            workColumn = dColumn - 1;
        }
        CharPosition pos = new CharPosition();
        pos.column = workColumn;
        pos.line = workLine;
        pos.index = index;
        return pos;
    }

    /**
     * From the given position to find forward in text
     * @param start Given position
     * @param line Querying line
     * @param column Querying column
     * @return The querying position
     */
    private CharPosition findLiCoForward(CharPosition start,int line,int column) {
        if(start.line > line) {
            throw new IllegalArgumentException("can not find backward from findLiCoForward()");
        }
        int workLine = start.line;
        int workIndex = start.index;
        {
            //Make index to to left of line
            workIndex = workIndex - start.column;
        }
        while(workLine < line) {
            workIndex += _c.getColumnCount(workLine) + 1;
            workLine ++;
        }
        CharPosition pos = new CharPosition();
        pos.column = 0;
        pos.line = workLine;
        pos.index = workIndex;
        return findInLine(pos, line, column);
    }

    /**
     * From the given position to find backward in text
     * @param start Given position
     * @param line Querying line
     * @param column Querying column
     * @return The querying position
     */
    private CharPosition findLiCoBackward(CharPosition start,int line,int column) {
        if(start.line < line) {
            throw new IllegalArgumentException("can not find forward from findLiCoBackward()");
        }
        int workLine = start.line;
        int workIndex = start.index;
        {
            //Make index to the left of line
            workIndex = workIndex - start.column;
        }
        while(workLine > line) {
            workIndex -= _c.getColumnCount(workLine - 1) + 1;
            workLine--;
        }
        CharPosition pos = new CharPosition();
        pos.column = 0;
        pos.line = workLine;
        pos.index = workIndex;
        return findInLine(pos, line, column);
    }

    /**
     * From the given position to find in this line
     * @param pos Given position
     * @param line Querying line
     * @param column Querying column
     * @return The querying position
     */
    private CharPosition findInLine(CharPosition pos,int line,int column) {
        if(pos.line != line) {
            throw new IllegalArgumentException("can not find other lines with findInLine()");
        }
        int index = pos.index - pos.column + column;
        CharPosition pos2 = new CharPosition();
        pos2.column = column;
        pos2.line = line;
        pos2.index = index;
        return pos2;
    }

    /**
     * Add new cache
     * @param pos New cache
     */
    private void push(CharPosition pos) {
        _cache.add(pos);
        if(_maxSize <= 0) {
            return;
        }
        while(_cache.size() > _maxSize) {
            _cache.remove(0);
        }
    }

    /**
     * Set max cache size
     * @param maxSize max cache size
     */
    protected void setMaxCacheSize(int maxSize) {
        _maxSize = maxSize;
    }

    /**
     * Get max cache size
     * @return max cache size
     */
    protected int getMaxCacheSize() {
        return _maxSize;
    }

    /**
     * For NoCacheIndexer
     * @param handle Whether handle changes to refresh cache
     */
    protected void setHandleEvent(boolean handle) {
        _handle = handle;
    }

    /**
     * For NoCacheIndexer
     * @return whether handle changes
     */
    protected boolean isHandleEvent() {
        return _handle;
    }

    @Override
    public int getCharIndex(int line, int column) {
        return getCharPosition(line,column).index;
    }

    @Override
    public int getCharLine(int index) {
        return getCharPosition(index).line;
    }

    @Override
    public int getCharColumn(int index) {
        return getCharPosition(index).column;
    }

    @Override
    public CharPosition getCharPosition(int index) {
        _throw();
        _c.checkIndex(index);
        CharPosition pos = findNearestByIndex(index);
        CharPosition res;
        if(pos.index == index) {
            return pos;
        }else if(pos.index < index) {
            res = findIndexForward(pos, index);
        }else {
            res = findIndexBackward(pos, index);
            if(_lex){
                return res;
            }
        }
        if(!_lex && Math.abs(index - pos.index) >= _switchIndex) {
            push(res);
        }
        return res;
    }

    @Override
    public CharPosition getCharPosition(int line, int column) {
        _throw();
        _c.checkLineAndColumn(line, column, true);
        CharPosition pos = findNearestByLine(line);
        CharPosition res;
        if(pos.line == line) {
            if(pos.column == column) {
                return pos;
            }
            return findInLine(pos,line,column);
        }else if(pos.line < line) {
            res = findLiCoForward(pos, line, column);
        }else {
            res = findLiCoBackward(pos, line, column);
        }
        if(Math.abs(pos.line - line) > _switchLine) {
            push(res);
        }
        return res;
    }

    @Override
    public void beforeReplace(Content content) {
        //Do nothing
    }

    @Override
    public void afterInsert(Content content, int startLine, int startColumn, int endLine, int endColumn,
                            CharSequence insertedContent) {
        if(isHandleEvent()) {
            for(CharPosition pos : _cache) {
                /*if(pos.line < startLine) {
                    //There is nothing to do with this CharPosition
                }else */
                if(pos.line == startLine){
                    if (pos.column >= startColumn) {
                        pos.index += insertedContent.length();
                        pos.line += endLine - startLine;
                        pos.column = endColumn + pos.column - startColumn;
                    }
                }else if(pos.line > startLine) {
                    pos.index += insertedContent.length();
                    pos.line += endLine - startLine;
                    //pos.column = pos.column;//may be an error here...
                    //Rose tip !!!!!!!!!!!!
                }
            }
        }
        _detectException();
    }

    @Override
    public void afterDelete(Content content, int startLine, int startColumn, int endLine, int endColumn,
                            CharSequence deletedContent) {
        if(isHandleEvent()) {
            List<CharPosition> garbbages = new ArrayList<>();
            for(CharPosition pos : _cache) {
                /*if(pos.line < startLine) {

                }else */
                if(pos.line == startLine) {
                    if(pos.column >= startColumn)
                        garbbages.add(pos);
                }else if(pos.line > startLine) {
                    garbbages.add(pos);
					/*
                    if(pos.line < endLine) {
                        garbbages.add(pos);
                    }else if(pos.line == endLine) {
                        garbbages.add(pos);
                        //Here should be a wonderful change...
                    }else {
                        pos.index -= deletedContent.length();
                        pos.line -= endLine - startLine;
                        //pos.column = pos.column;//This may be an error
                        //Rose tip !!!!!!!!!!!!
                    }*/
                }
            }
            _cache.removeAll(garbbages);
            garbbages.clear();
        }
        _detectException();
    }

}

