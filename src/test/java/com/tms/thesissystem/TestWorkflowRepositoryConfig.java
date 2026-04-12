package com.tms.thesissystem;

import com.tms.thesissystem.application.port.WorkflowRepository;
import com.tms.thesissystem.domain.model.ApprovalRecord;
import com.tms.thesissystem.domain.model.ApprovalStage;
import com.tms.thesissystem.domain.model.AuditEntry;
import com.tms.thesissystem.domain.model.Notification;
import com.tms.thesissystem.domain.model.Plan;
import com.tms.thesissystem.domain.model.PlanStatus;
import com.tms.thesissystem.domain.model.Review;
import com.tms.thesissystem.domain.model.Topic;
import com.tms.thesissystem.domain.model.TopicStatus;
import com.tms.thesissystem.domain.model.User;
import com.tms.thesissystem.domain.model.UserRole;
import com.tms.thesissystem.domain.model.WeeklyTask;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@TestConfiguration
public class TestWorkflowRepositoryConfig {
    @Bean
    @Primary
    WorkflowRepository workflowRepository() {
        return new TestWorkflowRepository();
    }

    static class TestWorkflowRepository implements WorkflowRepository {
        private final Map<Long, User> users = new LinkedHashMap<>();
        private final Map<Long, Topic> topics = new LinkedHashMap<>();
        private final Map<Long, Plan> plans = new LinkedHashMap<>();
        private final Map<Long, Review> reviews = new LinkedHashMap<>();
        private final Map<Long, Notification> notifications = new LinkedHashMap<>();
        private final Map<Long, AuditEntry> audits = new LinkedHashMap<>();

        private final AtomicLong topicSequence = new AtomicLong(10);
        private final AtomicLong planSequence = new AtomicLong(20);
        private final AtomicLong reviewSequence = new AtomicLong(30);
        private final AtomicLong notificationSequence = new AtomicLong(40);
        private final AtomicLong auditSequence = new AtomicLong(50);

        private record SeedStudent(String firstName, String lastName, String email, String loginId) {
        }

        private record SeedTeacher(String firstName, String lastName, String email, String loginId) {
        }

        TestWorkflowRepository() {
            seed();
        }

        private void seed() {
            LocalDateTime now = LocalDateTime.now();
            List<SeedStudent> students = studentSeeds();
            List<SeedTeacher> teachers = teacherSeeds();
            for (int index = 0; index < students.size(); index++) {
                SeedStudent student = students.get(index);
                long id = 100001L + index;
                users.put(id, new User(id, UserRole.STUDENT, student.loginId(), student.firstName(), student.lastName(), student.email(), "Software Engineering", "B.SE"));
            }
            for (int index = 0; index < teachers.size(); index++) {
                SeedTeacher teacher = teachers.get(index);
                long id = 200001L + index;
                users.put(id, new User(id, UserRole.TEACHER, teacher.loginId(), teacher.firstName(), teacher.lastName(), teacher.email(), "Software Engineering", "B.SE"));
            }
            User studentA = users.get(100001L);
            User studentB = users.get(100002L);
            User studentC = users.get(100003L);
            User teacherA = users.get(200001L);
            User teacherB = users.get(200002L);
            User teacherC = users.get(200003L);
            User departmentA = new User(300001L, UserRole.DEPARTMENT, "sisi-admin", "Програм", "админ", "sisi.admin@tms.mn", "Software Engineering", "B.SE");
            users.put(departmentA.id(), departmentA);

            Topic pendingTeacherTopic = Topic.studentProposal(
                    1L,
                    "Distributed Thesis Workflow",
                    "Оюутны санал болгосон дипломын workflow automation сэдэв.",
                    "B.SE",
                    studentA,
                    now.minusDays(2)
            );

            Topic availableTeacherTopic = new Topic(
                    2L,
                    "Event-driven Student Research Tracker",
                    "Багшийн батлагдсан, оюутан сонгож болох сэдэв.",
                    "B.SE",
                    teacherA.id(),
                    teacherA.fullName(),
                    UserRole.TEACHER,
                    null,
                    null,
                    teacherA.id(),
                    teacherA.fullName(),
                    TopicStatus.AVAILABLE,
                    now.minusDays(5),
                    now.minusDays(5),
                    List.of(new ApprovalRecord(ApprovalStage.DEPARTMENT, departmentA.id(), departmentA.fullName(), true, "Catalog approved", now.minusDays(4)))
            );

            Topic availableTeacherTopicB = new Topic(
                    4L,
                    "AI-based Thesis Workflow Automation",
                    "Оюутан сонгож болох багшийн батлагдсан сэдэв.",
                    "B.SE",
                    teacherB.id(),
                    teacherB.fullName(),
                    UserRole.TEACHER,
                    null,
                    null,
                    teacherB.id(),
                    teacherB.fullName(),
                    TopicStatus.AVAILABLE,
                    now.minusDays(6),
                    now.minusDays(6),
                    List.of(new ApprovalRecord(ApprovalStage.DEPARTMENT, departmentA.id(), departmentA.fullName(), true, "Catalog approved", now.minusDays(5)))
            );

            Topic availableTeacherTopicC = new Topic(
                    5L,
                    "Data-driven Research Planning",
                    "Өгөгдөлд суурилсан судалгааны төлөвлөлтийн сэдэв.",
                    "B.DS",
                    teacherC.id(),
                    teacherC.fullName(),
                    UserRole.TEACHER,
                    null,
                    null,
                    teacherC.id(),
                    teacherC.fullName(),
                    TopicStatus.AVAILABLE,
                    now.minusDays(7),
                    now.minusDays(7),
                    List.of(new ApprovalRecord(ApprovalStage.DEPARTMENT, departmentA.id(), departmentA.fullName(), true, "Catalog approved", now.minusDays(6)))
            );

            Topic approvedStudentTopic = new Topic(
                    3L,
                    "Layered Architecture for Graduation Management",
                    "Тэнхим, багш, оюутны approval flow-тай систем.",
                    "B.SE",
                    studentB.id(),
                    studentB.fullName(),
                    UserRole.STUDENT,
                    studentB.id(),
                    studentB.fullName(),
                    teacherA.id(),
                    teacherA.fullName(),
                    TopicStatus.APPROVED,
                    now.minusDays(12),
                    now.minusDays(10),
                    List.of(
                            new ApprovalRecord(ApprovalStage.TEACHER, teacherA.id(), teacherA.fullName(), true, "Teacher approved", now.minusDays(11)),
                            new ApprovalRecord(ApprovalStage.DEPARTMENT, departmentA.id(), departmentA.fullName(), true, "Department approved", now.minusDays(10))
                    )
            );

            topics.put(1L, pendingTeacherTopic);
            topics.put(2L, availableTeacherTopic);
            topics.put(3L, approvedStudentTopic);
            topics.put(4L, availableTeacherTopicB);
            topics.put(5L, availableTeacherTopicC);

            List<WeeklyTask> seededTasks = java.util.stream.IntStream.rangeClosed(1, 15)
                    .mapToObj(week -> new WeeklyTask(week, "Week " + week, "Deliverable " + week, "Focus " + week))
                    .toList();

            Plan approvedPlan = new Plan(
                    1L,
                    approvedStudentTopic.id(),
                    approvedStudentTopic.title(),
                    studentB.id(),
                    studentB.fullName(),
                    PlanStatus.APPROVED,
                    seededTasks,
                    List.of(
                            new ApprovalRecord(ApprovalStage.TEACHER, teacherA.id(), teacherA.fullName(), true, "Teacher approved", now.minusDays(9)),
                            new ApprovalRecord(ApprovalStage.DEPARTMENT, departmentA.id(), departmentA.fullName(), true, "Department approved", now.minusDays(8))
                    ),
                    now.minusDays(10),
                    now.minusDays(8)
            );

            plans.put(1L, approvedPlan);
            reviews.put(1L, new Review(1L, approvedPlan.id(), 4, teacherA.id(), teacherA.fullName(), 92, "Судалгааны хэсэг сайн.", now.minusDays(1)));
            notifications.put(1L, new Notification(1L, studentB.id(), "Төлөвлөгөө батлагдсан", "15 долоо хоногийн төлөвлөгөөг тэнхим баталгаажууллаа.", now.minusHours(6)));
            audits.put(1L, new AuditEntry(1L, "PLAN", approvedPlan.id(), "PLAN_APPROVED", departmentA.fullName(), "Тэнхим төлөвлөгөөг эцэслэн баталлаа.", now.minusHours(6)));
        }

        private List<SeedStudent> studentSeeds() {
            return List.of(
                    new SeedStudent("Ану", "Бат-Эрдэнэ", "anu.bat-erdene@tms.mn", "22b1num0027"),
                    new SeedStudent("Тэмүүлэн", "Дорж", "temuulen.dorj@tms.mn", "22b1num0028"),
                    new SeedStudent("Номин", "Эрдэнэ", "nomin.erdene@tms.mn", "22b1num0029"),
                    new SeedStudent("Марал", "Мөнхбат", "maral.munkhbat@tms.mn", "22b1num0030"),
                    new SeedStudent("Энхжин", "Ганзориг", "enkhjin.ganzorig@tms.mn", "22b1num0031"),
                    new SeedStudent("Билгүүн", "Түвшинжаргал", "bilguun.tuvshinjargal@tms.mn", "22b1num0032"),
                    new SeedStudent("Сувд", "Лхагвасүрэн", "suvd.lkhagvasuren@tms.mn", "22b1num0033"),
                    new SeedStudent("Одгэрэл", "Батсүх", "odgerel.batsukh@tms.mn", "22b1num0034"),
                    new SeedStudent("Мөнхжин", "Пүрэвдорж", "munkhjin.purevdorj@tms.mn", "22b1num0035"),
                    new SeedStudent("Уянга", "Сандаг", "uyanga.sandag@tms.mn", "22b1num0036"),
                    new SeedStudent("Төгөлдөр", "Алтанхуяг", "tuguldur.altankhuyag@tms.mn", "22b1num0037"),
                    new SeedStudent("Хүслэн", "Очирбат", "khuslen.ochirbat@tms.mn", "22b1num0038"),
                    new SeedStudent("Ариунболд", "Нямдорж", "ariunbold.nyamdorj@tms.mn", "22b1num0039"),
                    new SeedStudent("Намуун", "Жаргалсайхан", "namuun.jargalsaikhan@tms.mn", "22b1num0040"),
                    new SeedStudent("Бат-Оргил", "Эрдэнэбат", "bat-orgil.erdenebat@tms.mn", "22b1num0041"),
                    new SeedStudent("Дөлгөөн", "Мягмарсүрэн", "dulguun.myagmarsuren@tms.mn", "22b1num0042"),
                    new SeedStudent("Жавхлан", "Бямбасүрэн", "javkhlan.byambasuren@tms.mn", "22b1num0043"),
                    new SeedStudent("Пүрэвсүрэн", "Ганбат", "purevsuren.ganbat@tms.mn", "22b1num0044"),
                    new SeedStudent("Саруул", "Даваажав", "saruul.davaajav@tms.mn", "22b1num0045"),
                    new SeedStudent("Анхбаяр", "Болдбаатар", "ankhbayar.boldbaatar@tms.mn", "22b1num0046"),
                    new SeedStudent("Мишээл", "Төмөрбаатар", "misheel.tumurbaatar@tms.mn", "22b1num0047"),
                    new SeedStudent("Амин-Эрдэнэ", "Цогтбаяр", "amin-erdene.tsogtbayar@tms.mn", "22b1num0048"),
                    new SeedStudent("Ивээл", "Энхтүвшин", "iveel.enkhtuvshin@tms.mn", "22b1num0049"),
                    new SeedStudent("Сэцэн", "Баттулга", "setsen.battulga@tms.mn", "22b1num0050"),
                    new SeedStudent("Тэнүүн", "Адьяа", "tenuun.adiyaa@tms.mn", "22b1num0051"),
                    new SeedStudent("Гэгээ", "Чинзориг", "gegee.chinzorig@tms.mn", "22b1num0052"),
                    new SeedStudent("Хонгор", "Амарбаясгалан", "khongor.amarbayasgalan@tms.mn", "22b1num0053"),
                    new SeedStudent("Мөнгөнцэцэг", "Самдан", "munguntsetseg.samdan@tms.mn", "22b1num0054"),
                    new SeedStudent("Эрдэнэсувд", "Рэнцэн", "erdenesuvd.rentsen@tms.mn", "22b1num0055"),
                    new SeedStudent("Тэлмүүн", "Цэрэндаш", "telmuun.tserendash@tms.mn", "22b1num0056"),
                    new SeedStudent("Ариунзул", "Гомбосүрэн", "ariunzul.gombosuren@tms.mn", "22b1num0057"),
                    new SeedStudent("Хулан", "Лувсан", "khulan.luvsan@tms.mn", "22b1num0058"),
                    new SeedStudent("Мөнхтөр", "Сүхбаатар", "munkhtur.sukhbaatar@tms.mn", "22b1num0059"),
                    new SeedStudent("Содном", "Чулуунбат", "sodnom.chuluunbat@tms.mn", "22b1num0060"),
                    new SeedStudent("Ирмүүн", "Цэндсүрэн", "irmuun.tsendsuren@tms.mn", "22b1num0061"),
                    new SeedStudent("Наранзул", "Очирхуяг", "naranzul.ochirkhuyag@tms.mn", "22b1num0062"),
                    new SeedStudent("Тэмүүжин", "Галбадрах", "temuujin.galbadrah@tms.mn", "22b1num0063"),
                    new SeedStudent("Сэлэнгэ", "Гэрэлтуяа", "selenge.gereltuya@tms.mn", "22b1num0064"),
                    new SeedStudent("Даваахүү", "Цогзолмаа", "davaakhuu.tsogzolmaa@tms.mn", "22b1num0065"),
                    new SeedStudent("Оюунчимэг", "Төрбат", "oyuunchimeg.turbat@tms.mn", "22b1num0066"),
                    new SeedStudent("Баянмөнх", "Эрдэнэчимэг", "bayanmunkh.erdenechimeg@tms.mn", "22b1num0067"),
                    new SeedStudent("Цэлмэг", "Сайнбаяр", "tselmeg.sainbayar@tms.mn", "22b1num0068"),
                    new SeedStudent("Ган-Эрдэнэ", "Тогтох", "gan-erdene.togtokh@tms.mn", "22b1num0069"),
                    new SeedStudent("Энэрэл", "Мөнгөншагай", "enerel.mungunshagai@tms.mn", "22b1num0070"),
                    new SeedStudent("Чингүүн", "Отгонбаяр", "chinguun.otgonbayar@tms.mn", "22b1num0071"),
                    new SeedStudent("Алтанзаяа", "Цэрэндолгор", "altanzayaa.tserendolgor@tms.mn", "22b1num0072"),
                    new SeedStudent("Отгончимэг", "Хишигт", "otgonchimeg.khishigt@tms.mn", "22b1num0073"),
                    new SeedStudent("Золбоо", "Түвдэндорж", "zolboo.tuvdendorj@tms.mn", "22b1num0074"),
                    new SeedStudent("Мөнх-Оргил", "Зоригт", "munkh-orgil.zorigt@tms.mn", "22b1num0075"),
                    new SeedStudent("Хулангоо", "Чойжилсүрэн", "khulangoo.choijilsuren@tms.mn", "22b1num0076"),
                    new SeedStudent("Ундрах", "Энх-Амгалан", "undrakh.enkh-amgalan@tms.mn", "22b1num0077"),
                    new SeedStudent("Батчимэг", "Жамъян", "batchimeg.jamyan@tms.mn", "22b1num0078"),
                    new SeedStudent("Түвшинтөгс", "Наранбаатар", "tuvshintugs.naranbaatar@tms.mn", "22b1num0079"),
                    new SeedStudent("Содгэрэл", "Дашдондог", "sodgerel.dashdondog@tms.mn", "22b1num0080"),
                    new SeedStudent("Баясгалан", "Хүрэлбаатар", "bayasgalan.khurelbaatar@tms.mn", "22b1num0081"),
                    new SeedStudent("Эгшиглэн", "Гончиг", "egshiglen.gonchig@tms.mn", "22b1num0082"),
                    new SeedStudent("Сүндэр", "Эрдэнэпүрэв", "sunder.erdenepurev@tms.mn", "22b1num0083"),
                    new SeedStudent("Төгс-Эрдэнэ", "Насанбуян", "tugs-erdene.nasanbuyan@tms.mn", "22b1num0084"),
                    new SeedStudent("Оюунгэрэл", "Түмэнжаргал", "oyungerel.tumenjargal@tms.mn", "22b1num0085"),
                    new SeedStudent("Золзаяа", "Сэргэлэн", "zolzayaa.sergelen@tms.mn", "22b1num0086"),
                    new SeedStudent("Эрдэнэболд", "Базар", "erdenebold.bazar@tms.mn", "22b1num0087"),
                    new SeedStudent("Мөнхдөл", "Гэрэлчулуун", "munkhdul.gerelchuluun@tms.mn", "22b1num0088"),
                    new SeedStudent("Гэрэлмаа", "Дамбадаржаа", "gerelmaa.dambadarjaa@tms.mn", "22b1num0089"),
                    new SeedStudent("Бадамцэцэг", "Лхасүрэн", "badamtsetseg.lkhasuren@tms.mn", "22b1num0090"),
                    new SeedStudent("Отгонтөгс", "Мөнхсайхан", "otgontugs.munkhsaikhan@tms.mn", "22b1num0091"),
                    new SeedStudent("Сувд-Эрдэнэ", "Борхүү", "suvd-erdene.borkhuu@tms.mn", "22b1num0092"),
                    new SeedStudent("Тэнгэр", "Шинэбаяр", "tenger.shinebayar@tms.mn", "22b1num0093"),
                    new SeedStudent("Гүнжид", "Бумцэнд", "gunjid.bumtsend@tms.mn", "22b1num0094"),
                    new SeedStudent("Жаргалмаа", "Сэржмядаг", "jargalmaa.serjmyadag@tms.mn", "22b1num0095"),
                    new SeedStudent("Чинзориг", "Хатанбаатар", "chinzorig.khatanbaatar@tms.mn", "22b1num0096"),
                    new SeedStudent("Түвшинжаргал", "Эрдэнэ-Очир", "tuvshinjargal.erdene-ochir@tms.mn", "22b1num0097"),
                    new SeedStudent("Энхсаран", "Норов", "enkhsaran.norov@tms.mn", "22b1num0098"),
                    new SeedStudent("Батнасан", "Мэндсайхан", "batnasan.mendsaikhan@tms.mn", "22b1num0099"),
                    new SeedStudent("Солонго", "Цогт", "solongo.tsogt@tms.mn", "22b1num0100"),
                    new SeedStudent("Урангоо", "Даваасамбуу", "urangoo.davaasambuu@tms.mn", "22b1num0101"),
                    new SeedStudent("Эрдэнэзул", "Амгаланбаатар", "erdenezul.amgalanbaatar@tms.mn", "22b1num0102"),
                    new SeedStudent("Батсүрэн", "Нармандах", "batsuren.narmandakh@tms.mn", "22b1num0103"),
                    new SeedStudent("Гантулга", "Төгөлдөр", "gantulga.tuguldur@tms.mn", "22b1num0104"),
                    new SeedStudent("Сарнай", "Отгонсүх", "sarnai.otgonsukh@tms.mn", "22b1num0105"),
                    new SeedStudent("Энхриймаа", "Гомбожав", "enkhriimaa.gombojav@tms.mn", "22b1num0106"),
                    new SeedStudent("Мөнхцацрал", "Батдэлгэр", "munkhtsatsral.batdelger@tms.mn", "22b1num0107"),
                    new SeedStudent("Өлзийжаргал", "Ганпүрэв", "ulziijargal.ganpurev@tms.mn", "22b1num0108"),
                    new SeedStudent("Чимгээ", "Төрмөнх", "chimgee.turmunkh@tms.mn", "22b1num0109"),
                    new SeedStudent("Нарансолонго", "Лхагважав", "naransolongo.lkhagvajav@tms.mn", "22b1num0110"),
                    new SeedStudent("Дэлгэрзаяа", "Баяржаргал", "delgerzayaa.bayarjargal@tms.mn", "22b1num0111"),
                    new SeedStudent("Сүхбат", "Мөнхбаатар", "sukhbat.munkhbaatar@tms.mn", "22b1num0112"),
                    new SeedStudent("Баярцэцэг", "Чинбат", "bayartsetseg.chinbat@tms.mn", "22b1num0113"),
                    new SeedStudent("Оргилуун", "Отгонжаргал", "orgiluun.otgonjargal@tms.mn", "22b1num0114"),
                    new SeedStudent("Анужин", "Цэрэнпил", "anujin.tserenpil@tms.mn", "22b1num0115"),
                    new SeedStudent("Тодгэрэл", "Жигжид", "todgerel.jigjid@tms.mn", "22b1num0116"),
                    new SeedStudent("Цэлмүүн", "Баясгалант", "tselmuun.bayasgalant@tms.mn", "22b1num0117"),
                    new SeedStudent("Сарантуяа", "Гүррагчаа", "sarantuyaa.gurragchaa@tms.mn", "22b1num0118"),
                    new SeedStudent("Амарбаяр", "Нямбуу", "amarbayar.nyambuu@tms.mn", "22b1num0119"),
                    new SeedStudent("Нандин-Эрдэнэ", "Туяацэцэг", "nandin-erdene.tuyaatsetseg@tms.mn", "22b1num0120"),
                    new SeedStudent("Бүтэнбаяр", "Цэнд-Аюуш", "butenbayar.tsend-ayuush@tms.mn", "22b1num0121"),
                    new SeedStudent("Энхмэнд", "Батжаргал", "enkhmend.batjargal@tms.mn", "22b1num0122"),
                    new SeedStudent("Гэрэлсайхан", "Лодой", "gerelsaikhan.lodoi@tms.mn", "22b1num0123"),
                    new SeedStudent("Мишээлт", "Өсөхбаяр", "misheelt.usukhbayar@tms.mn", "22b1num0124"),
                    new SeedStudent("Оюундэлгэр", "Мөнгөнцог", "oyuundelger.munguntsog@tms.mn", "22b1num0125"),
                    new SeedStudent("Тэмүүн", "Баатарсүрэн", "temuun.baatarsuren@tms.mn", "22b1num0126")
            );
        }

        private List<SeedTeacher> teacherSeeds() {
            return List.of(
                    new SeedTeacher("Энх", "Сүрэн", "enkh.suren@tms.mn", "tch001"),
                    new SeedTeacher("Болор", "Наран", "bolor.naran@tms.mn", "tch002"),
                    new SeedTeacher("Саруул", "Мөнх", "saruul.munkh@tms.mn", "tch003"),
                    new SeedTeacher("Ганболд", "Батбаяр", "ganbold.batbayar@tms.mn", "tch004"),
                    new SeedTeacher("Отгонжаргал", "Сүх", "otgonjargal.sukh@tms.mn", "tch005"),
                    new SeedTeacher("Мөнх-Эрдэнэ", "Түвшин", "munkh-erdene.tuvshin@tms.mn", "tch006"),
                    new SeedTeacher("Нарантуяа", "Болд", "narantuya.bold@tms.mn", "tch007"),
                    new SeedTeacher("Батцэцэг", "Эрдэнэбат", "battsetseg.erdenebat@tms.mn", "tch008"),
                    new SeedTeacher("Төгсжаргал", "Гантулга", "tugsjargal.gantulga@tms.mn", "tch009"),
                    new SeedTeacher("Уянга", "Даваа", "uyanga.davaa@tms.mn", "tch010"),
                    new SeedTeacher("Чимгээ", "Батсайхан", "chimgee.batsaikhan@tms.mn", "tch011"),
                    new SeedTeacher("Халиун", "Мөнхтөр", "khaliun.munkhtur@tms.mn", "tch012"),
                    new SeedTeacher("Баярмаа", "Жаргал", "bayarmaa.jargal@tms.mn", "tch013"),
                    new SeedTeacher("Даваадорж", "Энхболд", "davaadorj.enkhbold@tms.mn", "tch014"),
                    new SeedTeacher("Отгонбат", "Лхагва", "otgonbat.lkhagva@tms.mn", "tch015"),
                    new SeedTeacher("Пүрэв", "Очир", "purev.ochir@tms.mn", "tch016"),
                    new SeedTeacher("Солонго", "Баттөр", "solongo.battur@tms.mn", "tch017"),
                    new SeedTeacher("Жавзмаа", "Гэрэл", "javzmaa.gerel@tms.mn", "tch018"),
                    new SeedTeacher("Эрдэнэсайхан", "Төмөр", "erdenesaikhan.tumur@tms.mn", "tch019"),
                    new SeedTeacher("Нямсүрэн", "Алтанхуяг", "nyamsuren.altankhuyag@tms.mn", "tch020")
            );
        }

        @Override
        public List<User> findAllUsers() {
            return users.values().stream().sorted(Comparator.comparing(User::id)).toList();
        }

        @Override
        public List<User> findUsersByRole(UserRole role) {
            return users.values().stream().filter(user -> user.role() == role).sorted(Comparator.comparing(User::id)).toList();
        }

        @Override
        public Optional<User> findUserById(Long id) {
            return Optional.ofNullable(users.get(id));
        }

        @Override
        public Long nextTopicId() {
            return topicSequence.getAndIncrement();
        }

        @Override
        public List<Topic> findAllTopics() {
            return topics.values().stream().sorted(Comparator.comparing(Topic::updatedAt).reversed()).toList();
        }

        @Override
        public Optional<Topic> findTopicById(Long id) {
            return Optional.ofNullable(topics.get(id));
        }

        @Override
        public Topic saveTopic(Topic topic) {
            topics.put(topic.id(), topic);
            return topic;
        }

        @Override
        public Long nextPlanId() {
            return planSequence.getAndIncrement();
        }

        @Override
        public List<Plan> findAllPlans() {
            return plans.values().stream().sorted(Comparator.comparing(Plan::updatedAt).reversed()).toList();
        }

        @Override
        public Optional<Plan> findPlanById(Long id) {
            return Optional.ofNullable(plans.get(id));
        }

        @Override
        public Optional<Plan> findPlanByStudentId(Long studentId) {
            return plans.values().stream().filter(plan -> plan.studentId().equals(studentId)).findFirst();
        }

        @Override
        public Plan savePlan(Plan plan) {
            plans.put(plan.id(), plan);
            return plan;
        }

        @Override
        public Long nextReviewId() {
            return reviewSequence.getAndIncrement();
        }

        @Override
        public List<Review> findAllReviews() {
            return new ArrayList<>(reviews.values());
        }

        @Override
        public Review saveReview(Review review) {
            reviews.put(review.id(), review);
            return review;
        }

        @Override
        public Long nextNotificationId() {
            return notificationSequence.getAndIncrement();
        }

        @Override
        public List<Notification> findAllNotifications() {
            return new ArrayList<>(notifications.values());
        }

        @Override
        public Notification saveNotification(Notification notification) {
            notifications.put(notification.id(), notification);
            return notification;
        }

        @Override
        public Long nextAuditId() {
            return auditSequence.getAndIncrement();
        }

        @Override
        public List<AuditEntry> findAllAuditEntries() {
            return new ArrayList<>(audits.values());
        }

        @Override
        public AuditEntry saveAuditEntry(AuditEntry auditEntry) {
            audits.put(auditEntry.id(), auditEntry);
            return auditEntry;
        }
    }
}
