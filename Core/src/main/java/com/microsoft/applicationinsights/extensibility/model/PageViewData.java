//------------------------------------------------------------------------------
// <auto-generated>
//     This code was generated by a tool.
// 
//     Tool     : bondc, Version=3.0.1, Build=bond-git.retail.not
//     Template : Microsoft.Bond.Rules.dll#Java.tt
//     File     : com\microsoft\applicationinsights\extensibility\model\PageViewData.java
//
//     Changes to this file may cause incorrect behavior and will be lost when
//     the code is regenerated.
// </auto-generated>
//------------------------------------------------------------------------------
package com.microsoft.applicationinsights.extensibility.model;


/**
* *****************************************************************************                                 PageView type ***************************************************************************** Instance of PageEvent represents a generic action on a page like a button     click.     It is also the base type for PageView and AJAXCall.
*/
@SuppressWarnings("all")
public class PageViewData extends com.microsoft.applicationinsights.extensibility.model.EventData
{
    //
    // Fields
    //

    // 10: Optional string url
    private String url;

    // 20: Optional string duration
    private String duration;

    /**
     * @return current value of url property
     */
    public final String getUrl() {
        return this.url;
    }

    /**
     * @param value new value of url property
     */
    public final void setUrl(String value) {
        this.url = value;
    }

    /**
     * @return current value of duration property
     */
    public final String getDuration() {
        return this.duration;
    }

    /**
     * @param value new value of duration property
     */
    public final void setDuration(String value) {
        this.duration = value;
    }

    // Constructor
    public PageViewData()
    {
    }
} // class PageViewData