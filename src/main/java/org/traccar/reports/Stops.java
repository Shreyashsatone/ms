/*
 * Copyright 2017 Anton Tananaev (anton@traccar.org)
 * Copyright 2017 Andrey Kunitsyn (andrey@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.traccar.reports;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import org.apache.poi.ss.util.WorkbookUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.Context;
import org.traccar.Main;
import org.traccar.database.DeviceManager;
import org.traccar.database.IdentityManager;
import org.traccar.model.Device;
import org.traccar.model.Group;
import org.traccar.reports.model.DeviceReport;
import org.traccar.reports.model.StopReport;

public final class Stops {
    private static final Logger LOGGER = LoggerFactory.getLogger(Trips.class);

    private Stops() {
    }

    private static Collection<StopReport> detectStops(long deviceId, Date from, Date to) throws SQLException {
        boolean ignoreOdometer = Context.getDeviceManager()
                .lookupAttributeBoolean(deviceId, "report.ignoreOdometer", false, false, true);

        IdentityManager identityManager = Main.getInjector().getInstance(IdentityManager.class);
        DeviceManager deviceManager = Main.getInjector().getInstance(DeviceManager.class);

        return ReportUtils.detectTripsAndStops(
                identityManager, deviceManager, Context.getDataManager().getPositions(deviceId, from, to),
                Context.getTripsConfig(), ignoreOdometer, StopReport.class);
    }

    public static Collection<StopReport> getObjects(
            long userId, Collection<Long> deviceIds, Collection<Long> groupIds,
            Date from, Date to) throws SQLException {
        ReportUtils.checkPeriodLimit(from, to);
        ArrayList<StopReport> result = new ArrayList<>();
        for (long deviceId: ReportUtils.getDeviceList(deviceIds, groupIds)) {
            Context.getPermissionsManager().checkDevice(userId, deviceId);
            result.addAll(detectStops(deviceId, from, to));
        }
        return result;
    }

    public static void getExcel(
            OutputStream outputStream, long userId, Collection<Long> deviceIds, Collection<Long> groupIds,
            Date from, Date to) throws SQLException, IOException {
        ReportUtils.checkPeriodLimit(from, to);
        ArrayList<DeviceReport> devicesStops = new ArrayList<>();
        ArrayList<String> sheetNames = new ArrayList<>();
        for (long deviceId: ReportUtils.getDeviceList(deviceIds, groupIds)) {
            Context.getPermissionsManager().checkDevice(userId, deviceId);
            Collection<StopReport> stops = detectStops(deviceId, from, to);
            DeviceReport deviceStops = new DeviceReport();
            Device device = Context.getIdentityManager().getById(deviceId);
            deviceStops.setDeviceName(device.getName());
            sheetNames.add(WorkbookUtil.createSafeSheetName(deviceStops.getDeviceName()));
            if (device.getGroupId() != 0) {
                Group group = Context.getGroupsManager().getById(device.getGroupId());
                if (group != null) {
                    deviceStops.setGroupName(group.getName());
                }
            }
            deviceStops.setObjects(stops);
            devicesStops.add(deviceStops);
        }
        String templatePath = Context.getConfig().getString("report.templatesPath",
                "templates/export/");
        try (InputStream inputStream = new FileInputStream(templatePath + "/stops.xlsx")) {
            org.jxls.common.Context jxlsContext = ReportUtils.initializeContext(userId);
            jxlsContext.putVar("devices", devicesStops);
            jxlsContext.putVar("sheetNames", sheetNames);
            jxlsContext.putVar("from", from);
            jxlsContext.putVar("to", to);
            ReportUtils.processTemplateWithSheets(inputStream, outputStream, jxlsContext);
        }
    }

    public static void getPdf(OutputStream outputStream,
                              long userId, Collection<Long> deviceIds, Collection<Long> groupIds,
                              Date from, Date to) throws SQLException, IOException {
        ReportUtils.checkPeriodLimit(from, to);
        PdfDocument pdf = new PdfDocument(new PdfWriter(outputStream));
        Document document = new Document(pdf, PageSize.A4);
        document.getPdfDocument().setDefaultPageSize(PageSize.A4.rotate());
        pdf.addEventHandler(PdfDocumentEvent.START_PAGE,
                new ReportUtils.TextFooterEventHandler(document, userId));

        PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        //title
        SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm");
        SimpleDateFormat repDate = new SimpleDateFormat("yyyy-MM-dd");
        LOGGER.warn("Creating pdf...");
        for(long deviceId: ReportUtils.getDeviceList(deviceIds, groupIds)) {
            Device device = Context.getIdentityManager().getById(deviceId);
            Text title =
                    new Text("Report Type: Stops").setFont(font).setFontSize(16f);
            Text name =
                    new Text("Device: "+device.getName()).setFont(font).setFontSize(15f);
            Text dates = new Text("Period: "+ repDate.format(from) +
                    " to " + repDate.format(to)).setFont(font).setFontSize(14f);
            Paragraph tit = new Paragraph().add(title);
            Paragraph date = new Paragraph().add(dates);
            Paragraph nam = new Paragraph().add(name);
            document.add(tit);
            document.add(nam);
            document.add(date);
            //body
            Collection<StopReport> stops = detectStops(deviceId, from, to);
            Table table = new Table(5).useAllAvailableWidth();
            Cell deviceName = new Cell().add(new Paragraph("Device Name"))
                    .setBackgroundColor(ColorConstants.BLUE)
                    .setFontColor(ColorConstants.WHITE);
            Cell deviceSTm = new Cell().add(new Paragraph("Start Time"))
                    .setBackgroundColor(ColorConstants.BLUE)
                    .setFontColor(ColorConstants.WHITE);
            Cell deviceSAddr = new Cell().add(new Paragraph("Address"))
                    .setBackgroundColor(ColorConstants.BLUE)
                    .setFontColor(ColorConstants.WHITE);
            Cell deviceETm = new Cell().add(new Paragraph("End Time"))
                    .setBackgroundColor(ColorConstants.BLUE)
                    .setFontColor(ColorConstants.WHITE);
            Cell deviceDuration = new Cell().add(new Paragraph("Duration"))
                    .setBackgroundColor(ColorConstants.BLUE)
                    .setFontColor(ColorConstants.WHITE);

            table.addCell(deviceName);
            table.addCell(deviceSTm);
            table.addCell(deviceSAddr);
            table.addCell(deviceETm);
            table.addCell(deviceDuration);
            for (StopReport report : stops) {
                LOGGER.warn("populating table...");
                Cell reportDeviceName = new Cell().add(new Paragraph(report.getDeviceName()));
                Cell startTime = new Cell().add(new Paragraph(sdfTime.
                        format(report.getStartTime())));
                Cell sAddr, eAddr;
                try {
                    sAddr = new Cell().add(new Paragraph(report.getAddress()));
                } catch (Exception ex) {
                    LOGGER.error("Error: " + ex);
                    sAddr = new Cell().add(new Paragraph(report.getLatitude()
                            + "," + report.getLongitude()));
                }
                Cell endTime = new Cell().add(new Paragraph(sdfTime.
                        format(report.getEndTime())));
                Cell duration = new Cell().add(new Paragraph(String.format("%dhr %dmin",
                        TimeUnit.MILLISECONDS.toHours(report.getDuration()),
                        (report.getDuration() / (60*1000)) % 60)));
                //add cells to table
                table.addCell(reportDeviceName);
                table.addCell(startTime);
                table.addCell(sAddr);
                table.addCell(endTime);
                table.addCell(duration);

            }
            document.add(table);
            document.add(new AreaBreak());
            LOGGER.warn("closing...");
        }
        //close pdf
        document.close();
    }

}
