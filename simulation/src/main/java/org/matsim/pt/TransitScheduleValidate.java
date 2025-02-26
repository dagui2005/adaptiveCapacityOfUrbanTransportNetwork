package org.matsim.pt;

import org.matsim.pt2matsim.run.CheckMappedSchedulePlausibility;

public class TransitScheduleValidate {
    public static void main(String[] args){
        String schedulePath = "src/main/resources/ChinaNormalAflddph1.residual transit schedule.xml";
        String networkPath = "src/main/resources/ChinaNormalAflddph1.residual coupled network.s.xml";
        String crs = "EPSG:32650";
        String outputFolder = "scenarios/scheduleValidate";
        CheckMappedSchedulePlausibility.main(new String[] {schedulePath, networkPath, crs, outputFolder});
    }
}
