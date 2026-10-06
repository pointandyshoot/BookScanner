package au.id.pointandyshoot.bookscanner.core;

import java.util.ArrayList;
import java.util.List;

/** Overlapping source-pixel sections; at least six sections for a normal shelf photo. */
public final class StillPlan {
    private StillPlan() {}
    public static List<int[]> sections(int width,int height){
        if(width<1||height<1)throw new IllegalArgumentException("Empty photo");
        int columns=Math.max(width>=600?(width>height?3:2):1,(int)Math.ceil(width/1000.0));
        int rows=Math.max(height>=600?(height>=width?3:2):1,(int)Math.ceil(height/1000.0));
        int cw=Math.min(width,(int)Math.ceil(width/(columns-(columns-1)*.30)));
        int ch=Math.min(height,(int)Math.ceil(height/(rows-(rows-1)*.30)));
        List<int[]> result=new ArrayList<>();
        for(int row=0;row<rows;row++)for(int col=0;col<columns;col++){
            int x=columns==1?0:Math.round((width-cw)*col/(float)(columns-1));
            int y=rows==1?0:Math.round((height-ch)*row/(float)(rows-1));
            result.add(new int[]{x,y,x+cw,y+ch});
        }
        return result;
    }
}
