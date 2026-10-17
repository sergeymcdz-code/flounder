package com.acme.sportplatform.security;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;

import javax.imageio.ImageIO;

class PrintFormsIntegrationTest extends Qa04SecurityFixture {
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.beans.factory.annotation.Qualifier("publicPdfCache")
    com.github.benmanes.caffeine.cache.Cache<
                    String, com.acme.sportplatform.documents.api.PdfDocument>
            publicCache;

    private void ageGroups() {
        jdbc.update(
                "update event_disciplines set"
                    + " age_groups_json='[{\"code\":\"young\",\"name\":\"2014–2015\",\"gender\":\"M\",\"birthYearFrom\":2014,\"birthYearTo\":2015}]'"
                    + " where id=?",
                id("discipline"));
        em.clear();
    }

    String path(String suffix) {
        return "/api/v1/events/" + ids.get("event") + suffix;
    }

    MvcResult pdf(String name, String query) throws Exception {
        return mvc.perform(
                        get(path("/documents/" + name + ".pdf") + query)
                                .with(authentication(auth("organizer"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();
    }

    String text(MvcResult result) throws Exception {
        try (var p = PDDocument.load(result.getResponse().getContentAsByteArray())) {
            return new PDFTextStripper().getText(p);
        }
    }

    int pages(MvcResult result) throws Exception {
        try (var p = PDDocument.load(result.getResponse().getContentAsByteArray())) {
            return p.getNumberOfPages();
        }
    }

    void enabled() {
        jdbc.update(
                "update events set settings_json='{\"diploma\":{\"enabled\":true}}' where id=?",
                id("event"));
        em.clear();
    }

    @Test
    void bibsAreStableVisibleInProtocolsAndLockedAfterStart() throws Exception {
        var url = path("/bib-numbers/assign");
        mvc.perform(
                        post(url)
                                .with(authentication(auth("organizer")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mode\":\"BY_NAME\",\"startFrom\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigned").value(1));
        mvc.perform(
                        post(url)
                                .with(authentication(auth("operator")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mode\":\"BY_NAME\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kept").value(1));
        mvc.perform(get(path("/bib-numbers")).with(authentication(auth("organizer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bibNumber").value(7));
        var startList = pdf("start-list", "");
        var results = pdf("results", "");
        assertThat(text(startList)).contains("№", "7", "Иванов");
        assertThat(text(results)).contains("№", "7", "Иванов");
        Files.createDirectories(Path.of("target/s09-samples"));
        Files.write(
                Path.of("target/s09-samples/start-list-bibs.pdf"),
                startList.getResponse().getContentAsByteArray());
        Files.write(
                Path.of("target/s09-samples/results-bibs.pdf"),
                results.getResponse().getContentAsByteArray());
        mvc.perform(
                        post(url)
                                .with(authentication(auth("organizer")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mode\":\"BY_NAME\",\"reassign\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("scheduling.bib_locked"));
        mvc.perform(
                        patch(path("/bib-numbers/" + ids.get("profile")))
                                .with(authentication(auth("organizer")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"bibNumber\":77}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bibNumber").value(77));
    }

    @Test
    void cardsHaveExactPaginationExcludeDraftsAndKeepWithdrawnRows() throws Exception {
        for (int i = 2; i <= 4; i++)
            jdbc.update(
                    "insert into"
                        + " competition_units(id,event_discipline_id,label,sequence_number,status,scheduled_at)"
                        + " values (?,?,'Заплыв',?, ?,now())",
                    UUID.randomUUID(),
                    id("discipline"),
                    i,
                    i == 4 ? "DRAFT" : "PUBLISHED");
        jdbc.update(
                "update competition_unit_entries set entry_status='WITHDRAWN' where id=?",
                id("entry"));
        em.clear();
        var a5 = pdf("heat-cards", "?format=A5");
        var a4 = pdf("heat-cards", "?format=A4x2");
        assertThat(pages(a5)).isEqualTo(3);
        assertThat(pages(a4)).isEqualTo(2);
        assertThat(text(a5)).contains("Заплыв 2", "Иванов", "снят").doesNotContain("Заплыв 4");
        assertThat(pages(pdf("heat-cards", "?disciplineId=" + ids.get("newDiscipline"))))
                .isEqualTo(1);
        try (var p = PDDocument.load(a5.getResponse().getContentAsByteArray())) {
            assertThat(p.getPage(0).getMediaBox().getWidth()).isBetween(419f, 421f);
        }
        var output = Path.of("target/s09-samples");
        Files.createDirectories(output);
        Files.write(output.resolve("heat-card-a5.pdf"), a5.getResponse().getContentAsByteArray());
        Files.write(
                output.resolve("heat-cards-a4x2.pdf"), a4.getResponse().getContentAsByteArray());
    }

    @Test
    void awardsKeepTiesFilterPlacesAndNeverPublishDraftResults() throws Exception {
        for (int i = 0; i < 3; i++) {
            String key = "extra" + i;
            ids.put(key, UUID.randomUUID().toString());
            ids.put(key + "p", UUID.randomUUID().toString());
            ids.put(key + "r", UUID.randomUUID().toString());
            ids.put(key + "e", UUID.randomUUID().toString());
            user(key, "participant");
            profile(key + "p", key);
            registration(key + "r", "event", "discipline", key + "p", key);
            jdbc.update(
                    "update profiles set last_name=? where id=?",
                    i == 0 ? "Ничья" : i == 1 ? "Четвёртый" : "Черновик",
                    id(key + "p"));
            UUID unit = id("unit");
            if (i == 2) {
                unit = UUID.randomUUID();
                jdbc.update(
                        "insert into"
                            + " competition_units(id,event_discipline_id,label,sequence_number,status)"
                            + " values (?,?,'Черновик',2,'DRAFT')",
                        unit,
                        id("discipline"));
            }
            jdbc.update(
                    "insert into"
                        + " competition_unit_entries(id,competition_unit_id,registration_id,lane_or_position)"
                        + " values (?,?,?,?)",
                    id(key + "e"),
                    unit,
                    id(key + "r"),
                    i + 2);
            jdbc.update(
                    "insert into"
                        + " results(id,competition_unit_entry_id,result_type,status,time_ms,final_place,place_in_age_group)"
                        + " values (?,?,'TIME','VALID',32150,?,?)",
                    UUID.randomUUID(),
                    id(key + "e"),
                    i == 1 ? 4 : 1,
                    i == 1 ? 4 : 1);
        }
        em.clear();
        enabled();
        ageGroups();
        var awards = pdf("awards", "?scope=ABSOLUTE");
        assertThat(text(awards))
                .contains("Ничья", "Иванов")
                .doesNotContain("Четвёртый", "Черновик");
        var pub =
                mvc.perform(get(publicUrl("/awards.pdf?scope=ABSOLUTE")))
                        .andExpect(status().isOk())
                        .andReturn();
        assertThat(text(pub)).contains("Ничья").doesNotContain("Черновик");
        assertThat(pages(pdf("diplomas", "?scope=ABSOLUTE"))).isEqualTo(2);
        Files.createDirectories(Path.of("target/s09-samples"));
        Files.write(
                Path.of("target/s09-samples/awards.pdf"),
                awards.getResponse().getContentAsByteArray());
    }

    @Test
    void settingsPreserveOtherFieldsRejectUnknownTokensAndApplyEncryptedBackground()
            throws Exception {
        jdbc.update(
                "update events set settings_json='{\"otherSetting\":42}' where id=?", id("event"));
        em.clear();
        mvc.perform(
                        patch(path(""))
                                .with(authentication(auth("organizer")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"diploma\":{\"enabled\":true,\"bodyTemplate\":\"{oops}\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("documents.invalid_template"));
        mvc.perform(
                        patch(path(""))
                                .with(authentication(auth("organizer")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"diploma\":{\"enabled\":true,\"showLogo\":true,\"bodyTemplate\":\"{lastName}"
                                            + " {firstName} {placeText} {city}\"}}"))
                .andExpect(status().isOk());
        em.clear();
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(new java.awt.Color(250, 245, 231));
        graphics.fillRect(0, 0, 16, 16);
        graphics.dispose();
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        mvc.perform(
                        multipart(path("/documents/diploma-background"))
                                .file(
                                        new MockMultipartFile(
                                                "file",
                                                "background.png",
                                                "image/png",
                                                bytes.toByteArray()))
                                .with(authentication(auth("organizer"))))
                .andExpect(status().isOk());
        em.flush();
        em.clear();
        assertThat(
                        json.readTree(
                                        jdbc.queryForObject(
                                                "select settings_json::text from events where id=?",
                                                String.class,
                                                id("event")))
                                .path("otherSetting")
                                .asInt())
                .isEqualTo(42);
        var sample = pdf("diplomas", "?sample=true");
        assertThat(pages(sample)).isEqualTo(1);
        assertThat(text(sample)).contains("Иванова Мария", "1-е", "Самара");
        try (var p = PDDocument.load(sample.getResponse().getContentAsByteArray())) {
            var preview = new org.apache.pdfbox.rendering.PDFRenderer(p).renderImageWithDPI(0, 72);
            int visibleInk = 0;
            for (int y = 220; y < 650; y++)
                for (int x = 50; x < 550; x++) {
                    var color = new java.awt.Color(preview.getRGB(x, y));
                    if (color.getRed() < 100 && color.getGreen() < 100 && color.getBlue() < 100)
                        visibleInk++;
                }
            assertThat(visibleInk)
                    .as("Diploma text must paint above the background image")
                    .isGreaterThan(400);
            var corner =
                    new java.awt.Color(
                            preview.getRGB(preview.getWidth() - 10, preview.getHeight() - 10));
            assertThat(corner.getRed()).isBetween(248, 252);
            assertThat(corner.getGreen()).isBetween(243, 247);
            assertThat(corner.getBlue()).isBetween(229, 233);
            assertThat(p.getPage(0).getResources().getXObjectNames())
                    .anySatisfy(
                            n -> {
                                var object = p.getPage(0).getResources().getXObject(n);
                                assertThat(object)
                                        .isInstanceOf(
                                                org.apache.pdfbox.pdmodel.graphics.image
                                                        .PDImageXObject.class);
                                assertThat(
                                                ((org.apache.pdfbox.pdmodel.graphics.image
                                                                        .PDImageXObject)
                                                                object)
                                                        .getWidth())
                                        .isEqualTo(16);
                            });
        }
        Files.createDirectories(Path.of("target/s09-samples"));
        Files.write(
                Path.of("target/s09-samples/diploma.pdf"),
                sample.getResponse().getContentAsByteArray());
    }

    @Test
    void privateFormsRejectForeignOrganizerAndWritesRejectArchived() throws Exception {
        for (var form : List.of("heat-cards", "awards", "diplomas"))
            mvc.perform(
                            get(path("/documents/" + form + ".pdf"))
                                    .with(authentication(auth("foreign_organizer"))))
                    .andExpect(status().isForbidden());
        jdbc.update("update events set status='ARCHIVED' where id=?", id("event"));
        em.clear();
        mvc.perform(
                        post(path("/bib-numbers/assign"))
                                .with(authentication(auth("organizer")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mode\":\"BY_NAME\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("events.archived"));
        assertThat(pages(pdf("heat-cards", ""))).isEqualTo(1);
        assertThat(pages(pdf("diplomas", "?sample=true"))).isEqualTo(1);
    }

    private void addAwardees(int count) {
        for (int i = 0; i < count; i++) {
            UUID profile = UUID.randomUUID(),
                    registration = UUID.randomUUID(),
                    entry = UUID.randomUUID();
            jdbc.update(
                    "insert into profiles(id,last_name,first_name,birth_date,gender,club_name)"
                        + " values (?,'Победитель','Иван','2014-01-01','M','Волна')",
                    profile);
            jdbc.update(
                    "insert into"
                        + " registrations(id,event_id,event_discipline_id,participant_profile_id,status,participant_snapshot,created_at,updated_at)"
                        + " values (?,?,?,?,'CONFIRMED','{}',now(),now())",
                    registration,
                    id("event"),
                    id("discipline"),
                    profile);
            jdbc.update(
                    "insert into"
                        + " competition_unit_entries(id,competition_unit_id,registration_id,lane_or_position)"
                        + " values (?,?,?,?)",
                    entry,
                    id("unit"),
                    registration,
                    i + 2);
            jdbc.update(
                    "insert into"
                        + " results(id,competition_unit_entry_id,result_type,status,time_ms,final_place,place_in_age_group)"
                        + " values (?,?,'TIME','VALID',32150,1,1)",
                    UUID.randomUUID(),
                    entry);
        }
        em.clear();
    }

    @Test
    void twoHundredDiplomasRenderWithinTwentySecondsAndEachHasOnePage() throws Exception {
        enabled();
        addAwardees(199);
        long start = System.nanoTime();
        var pdf = pdf("diplomas", "?scope=ABSOLUTE");
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertThat(millis).isLessThanOrEqualTo(20_000);
        assertThat(pages(pdf)).isEqualTo(200);
        System.out.println("S09: 200 diplomas in " + millis + " ms");
    }

    @Test
    void moreThanFiveHundredDiplomasRejectsBeforeRenderingWithFilterHint() throws Exception {
        enabled();
        addAwardees(500);
        mvc.perform(
                        get(path("/documents/diplomas.pdf?scope=ABSOLUTE"))
                                .with(authentication(auth("organizer"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("documents.too_many_pages"));
    }

    @Test
    void manualBibChangesDetectConflictsAndArchivedBackgroundUploadIsDenied() throws Exception {
        addAwardees(1);
        jdbc.update("update competition_units set status='PUBLISHED' where id=?", id("unit"));
        em.clear();
        mvc.perform(
                        post(path("/bib-numbers/assign"))
                                .with(authentication(auth("organizer")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mode\":\"BY_NAME\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigned").value(2));
        int other =
                jdbc.queryForObject(
                        "select bib_number from event_bib_numbers where event_id=? and"
                            + " profile_id<>?",
                        Integer.class,
                        id("event"),
                        id("profile"));
        mvc.perform(
                        patch(path("/bib-numbers/" + ids.get("profile")))
                                .with(authentication(auth("organizer")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"bibNumber\":" + other + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("scheduling.bib_taken"));
        mvc.perform(
                        patch(path("/bib-numbers/" + ids.get("profile")))
                                .with(authentication(auth("organizer")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"bibNumber\":77}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bibNumber").value(77));
        mvc.perform(
                        post(path("/bib-numbers/assign"))
                                .with(authentication(auth("organizer")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mode\":\"BY_NAME\",\"reassign\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigned").value(2));
        assertThat(
                        jdbc.queryForList(
                                "select bib_number from event_bib_numbers where event_id=? order by"
                                    + " bib_number",
                                Integer.class,
                                id("event")))
                .containsExactly(1, 2);
        jdbc.update("update events set status='ARCHIVED' where id=?", id("event"));
        em.clear();
        mvc.perform(
                        multipart(path("/documents/diploma-background"))
                                .file(
                                        new MockMultipartFile(
                                                "file", "bg.png", "image/png", new byte[] {1}))
                                .with(authentication(auth("organizer"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("events.archived"));
    }

    @Test
    void cachedPublicAwardsRecheckEventVisibilityAndDoNotExposeDraftDisciplines() throws Exception {
        ageGroups();
        var cached =
                mvc.perform(get(publicUrl("/awards.pdf"))).andExpect(status().isOk()).andReturn();
        assertThat(text(cached)).contains("Иванов");
        jdbc.update("update event_disciplines set status='DRAFT' where id=?", id("discipline"));
        em.clear();
        publicCache.invalidateAll();
        var hidden =
                mvc.perform(get(publicUrl("/awards.pdf"))).andExpect(status().isOk()).andReturn();
        assertThat(text(hidden)).doesNotContain("Иванов");
        jdbc.update("update events set status='DRAFT' where id=?", id("event"));
        em.clear();
        mvc.perform(get(publicUrl("/awards.pdf?scope=ABSOLUTE"))).andExpect(status().isNotFound());
    }

    @Test
    void cannotAttachAnotherEventsOrParticipantsFileAsBackground() throws Exception {
        mvc.perform(
                        patch(path(""))
                                .with(authentication(auth("organizer")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"diploma\":{\"backgroundFileKey\":\""
                                                + UUID.randomUUID()
                                                + "\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("documents.invalid_template"));
    }

    @Test
    void ageGroupAwardsAndDiplomasHonorGroupAndDisciplineFilters() throws Exception {
        jdbc.update(
                "update event_disciplines set"
                    + " age_groups_json='[{\"code\":\"young\",\"name\":\"2014–2015\",\"gender\":\"M\",\"birthYearFrom\":2014,\"birthYearTo\":2015}]'"
                    + " where id=?",
                id("discipline"));
        enabled();
        assertThat(text(pdf("awards", "?scope=AGE_GROUP"))).contains("2014–2015", "Иванов");
        assertThat(text(pdf("awards", "?disciplineId=" + ids.get("newDiscipline"))))
                .doesNotContain("Иванов");
        var diploma = pdf("diplomas", "?ageGroupCode=young");
        assertThat(pages(diploma)).isEqualTo(1);
        assertThat(text(diploma)).contains("Иванов");
        assertThat(text(pdf("diplomas", "?ageGroupCode=missing"))).doesNotContain("Иванов");
    }

    @Test
    void twoHundredDiplomasWithLargeBackgroundShareOneImageWithinTimeAndSizeLimits()
            throws Exception {
        enabled();
        addAwardees(199);
        byte[] background = noisyJpeg(2480, 3508);
        assertThat(background.length).isBetween(1_000_000, 2_200_000);
        mvc.perform(
                        multipart(path("/documents/diploma-background"))
                                .file(
                                        new MockMultipartFile(
                                                "file", "background.jpg", "image/jpeg", background))
                                .with(authentication(auth("organizer"))))
                .andExpect(status().isOk());
        em.flush();
        em.clear();

        long started = System.nanoTime();
        var response = pdf("diplomas", "?scope=ABSOLUTE");
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        byte[] bytes = response.getResponse().getContentAsByteArray();
        assertThat(elapsedMs).isLessThanOrEqualTo(20_000);
        assertThat(bytes.length).isLessThanOrEqualTo(3 * background.length);
        try (var document = PDDocument.load(bytes)) {
            assertThat(document.getNumberOfPages()).isEqualTo(200);
            var images = new HashSet<org.apache.pdfbox.cos.COSStream>();
            for (var page : document.getPages()) {
                int imageCount = 0;
                for (var name : page.getResources().getXObjectNames()) {
                    var object = page.getResources().getXObject(name);
                    if (object
                            instanceof
                            org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject image) {
                        assertThat(image.getWidth()).isEqualTo(2480);
                        assertThat(image.getHeight()).isEqualTo(3508);
                        images.add(image.getCOSObject());
                        imageCount++;
                    }
                }
                assertThat(imageCount).isEqualTo(1);
            }
            assertThat(images)
                    .as("All pages must reference one embedded background XObject")
                    .hasSize(1);
        }
        System.out.printf(
                Locale.ROOT,
                "S09 background: 200 diplomas, %d ms, JPEG %d bytes, PDF %d bytes%n",
                elapsedMs,
                background.length,
                bytes.length);
    }

    private byte[] noisyJpeg(int width, int height) throws IOException {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var random = new Random(20261017);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int grey = 40 + random.nextInt(196);
                image.setRGB(x, y, (grey << 16) | (grey << 8) | grey);
            }
        }
        var output = new ByteArrayOutputStream();
        var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (var stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            var parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(0.20f);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), parameters);
        } finally {
            writer.dispose();
            image.flush();
        }
        return output.toByteArray();
    }

    @Test
    void partialDiplomaPatchPreservesMissingAndNullFieldsAndCanExplicitlyClearBackground()
            throws Exception {
        enabled();
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        var png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        mvc.perform(
                        multipart(path("/documents/diploma-background"))
                                .file(
                                        new MockMultipartFile(
                                                "file",
                                                "background.png",
                                                "image/png",
                                                png.toByteArray()))
                                .with(authentication(auth("organizer"))))
                .andExpect(status().isOk());
        em.flush();
        em.clear();
        var previous =
                json.readTree(
                                jdbc.queryForObject(
                                        "select settings_json::text from events where id=?",
                                        String.class,
                                        id("event")))
                        .get("diploma");
        mvc.perform(
                        patch(path(""))
                                .with(authentication(auth("organizer")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"diploma\":{\"title\":\"Победителю\",\"enabled\":null,\"showLogo\":null,\"backgroundFileKey\":null}}"))
                .andExpect(status().isOk());
        em.flush();
        em.clear();
        var updated =
                json.readTree(
                                jdbc.queryForObject(
                                        "select settings_json::text from events where id=?",
                                        String.class,
                                        id("event")))
                        .get("diploma");
        assertThat(updated.path("enabled").asBoolean()).isTrue();
        assertThat(updated.path("title").asText()).isEqualTo("Победителю");
        assertThat(updated.path("bodyTemplate")).isEqualTo(previous.path("bodyTemplate"));
        assertThat(updated.path("backgroundFileKey")).isEqualTo(previous.path("backgroundFileKey"));
        mvc.perform(
                        patch(path(""))
                                .with(authentication(auth("organizer")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"diploma\":{\"backgroundFileKey\":\"\",\"enabled\":false,\"showLogo\":true}}"))
                .andExpect(status().isOk());
        em.flush();
        em.clear();
        var cleared =
                json.readTree(
                                jdbc.queryForObject(
                                        "select settings_json::text from events where id=?",
                                        String.class,
                                        id("event")))
                        .get("diploma");
        assertThat(cleared.hasNonNull("backgroundFileKey")).isFalse();
        assertThat(cleared.path("enabled").asBoolean()).isFalse();
        assertThat(cleared.path("showLogo").asBoolean()).isTrue();
    }

    @Test
    void publicAwardsIgnoreQueryVariationsAndShareOneCacheEntry() throws Exception {
        ageGroups();
        var first =
                mvc.perform(get(publicUrl("/awards.pdf"))).andExpect(status().isOk()).andReturn();
        assertThat(text(first)).contains("Иванов");
        for (int i = 0; i < 12; i++) {
            var next =
                    mvc.perform(
                                    get(
                                            publicUrl(
                                                    "/awards.pdf?scope=ABSOLUTE&places="
                                                            + (i + 10)
                                                            + "&disciplineId="
                                                            + UUID.randomUUID())))
                            .andExpect(status().isOk())
                            .andReturn();
            assertThat(next.getResponse().getContentAsByteArray())
                    .isEqualTo(first.getResponse().getContentAsByteArray());
        }
        assertThat(
                        publicCache.asMap().keySet().stream()
                                .filter(k -> k.contains(ids.get("event")) && k.endsWith(":awards")))
                .hasSize(1);
    }

    @Test
    void backgroundExceedingTwelveMegapixelsIsRejected() throws Exception {
        var image = new BufferedImage(4001, 3000, BufferedImage.TYPE_BYTE_GRAY);
        var png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        image.flush();
        mvc.perform(
                        multipart(path("/documents/diploma-background"))
                                .file(
                                        new MockMultipartFile(
                                                "file",
                                                "large.png",
                                                "image/png",
                                                png.toByteArray()))
                                .with(authentication(auth("organizer"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("documents.invalid_background"));
    }
}
