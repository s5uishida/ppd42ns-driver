package io.github.s5uishida.iot.device.ppd42ns.driver;

import java.text.SimpleDateFormat;
import java.util.Date;

/*
 * @author s5uishida
 *
 */
public class PPD42NSObservationData {
	private static final String dateFormat = "yyyy-MM-dd HH:mm:ss.SSS";
	private static final SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);

	private final Date startTime;
	private final Date endTime;
	private final float pcs;
	private final float ugm3;

	public PPD42NSObservationData(Date startTime, Date endTime, float pcs, float ugm3) {
		this.startTime = startTime;
		this.endTime = endTime;
		this.pcs = pcs;
		this.ugm3 = ugm3;
	}

	public Date getStartTime() {
		return startTime;
	}

	public String getStartTimeString() {
		return sdf.format(startTime);
	}

	public Date getEndTime() {
		return endTime;
	}

	public String getEndTimeString() {
		return sdf.format(endTime);
	}

	public long getObservationTimeMillis() {
		return endTime.getTime() - startTime.getTime();
	}

	public float getPcs() {
		return pcs;
	}

	public float getUgm3() {
		return ugm3;
	}

	@Override
	public String toString() {
		return "startTime:" + sdf.format(startTime) +
				" endTime:" + sdf.format(endTime) +
				" observationTimeMillis:" + (endTime.getTime() - startTime.getTime()) +
				" pcs:" + pcs +
				" ugm3:" + ugm3;
	}
}
