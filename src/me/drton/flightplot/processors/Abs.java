package me.drton.flightplot.processors;

import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.util.HashMap;
import java.util.Map;

/**
 * User: ton Date: 16.06.13 Time: 12:59
 */
public class Abs extends PlotProcessor {
    protected String[] param_Fields;
    protected double param_Scale;
    protected XYSeries series;

    @Override
    public Map<String, Object> getDefaultParameters() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("Fields", "LPOS.VX LPOS.VY");
        params.put("Scale", 1.0);
        return params;
    }

    @Override
    public void init() {
        param_Fields = ((String) parameters.get("Fields")).split(WHITESPACE_RE);
        param_Scale = (Double) parameters.get("Scale");
        series = createSeries();
    }

    @Override
    public void process(double time, Map<String, Object> update) {
        double s = 0.0;
        for (String field : param_Fields) {
            Object v = update.get(field);
            if (v != null && v instanceof Number) {
                double d = ((Number) v).doubleValue();
                s += d * d;
            } else {
                return;
            }
        }
        series.add(time, Math.sqrt(s) * param_Scale);
    }

    @Override
    public XYSeriesCollection getSeriesCollection() {
        return new XYSeriesCollection(series);
    }
}
