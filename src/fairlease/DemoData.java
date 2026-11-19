package fairlease;

import fairlease.exceptions.AgreementStateException;
import fairlease.exceptions.InvalidInputException;
import fairlease.model.Agreement;
import fairlease.model.BillType;
import fairlease.model.Condition;
import fairlease.model.Inspection;
import fairlease.model.InspectionEntry;
import fairlease.model.Item;
import fairlease.model.Landlord;
import fairlease.model.Payment;
import fairlease.model.Photo;
import fairlease.model.Property;
import fairlease.model.Tenant;
import fairlease.service.FairLease;
import fairlease.storage.PhotoVault;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.EnumSet;

/** Sample users, an apartment already rented to a student, and generated photos for the class presentation. */
final class DemoData {
    private DemoData() {
    }

    static void seed(FairLease lease, PhotoVault vault, Path samples)
            throws IOException, InvalidInputException, AgreementStateException {
        Files.createDirectories(samples);
        for (String[] picture : new String[][]{
                {"sofa-scratch", "Sofa - scratch on the left armrest"},
                {"mirror-crack", "Bathroom mirror - crack, lower corner"},
                {"water-meter", "Water meter"},
                {"electricity-meter", "Electricity meter"},
                {"gas-meter", "Gas meter"},
                {"water-bill", "Water bill"},
                {"electricity-bill", "Electricity bill"},
                {"table-broken-leg", "Kitchen table - broken leg"},
                {"room-overview", "Room overview"}}) {
            drawPicture(samples.resolve(picture[0] + ".png"), picture[1]);
        }

        Landlord maria = lease.registerLandlord("Maria Popescu", "maria@fairlease.ro", "+40722111222", "landlord1",
                "RO49AAAA1B31007593840000");
        Tenant andrei = lease.registerTenant("Andrei Ionescu", "andrei@student.ro", "+40733444555", "tenant1");
        lease.registerTenant("Ioana Rusu", "ioana@student.ro", "+40744666777", "tenant1");
        lease.registerAgent("Elena Marin", "agent@fairlease.ro", "+40755888999", "agent1",
                lease.getConfig().getAgentCode());

        Property apartment = lease.addProperty(maria, Property.Type.APARTMENT, "Str. Observatorului 12, ap. 4, Cluj-Napoca", 54);
        addItems(lease, apartment, "Living room", Item.Category.FURNITURE, "Sofa", "Coffee table");
        addItems(lease, apartment, "Living room", Item.Category.APPLIANCE, "TV");
        addItems(lease, apartment, "Kitchen", Item.Category.APPLIANCE, "Fridge", "Stove");
        addItems(lease, apartment, "Kitchen", Item.Category.FURNITURE, "Kitchen table");
        addItems(lease, apartment, "Bedroom", Item.Category.FURNITURE, "Bed", "Wardrobe", "Desk");
        addItems(lease, apartment, "Bathroom", Item.Category.APPLIANCE, "Washing machine");
        addItems(lease, apartment, "Bathroom", Item.Category.FIXTURE, "Mirror");

        Property studio = lease.addProperty(maria, Property.Type.STUDIO, "Str. Republicii 7, Cluj-Napoca", 28);
        addItems(lease, studio, "Main room", Item.Category.FURNITURE, "Bed", "Desk", "Wardrobe");
        addItems(lease, studio, "Kitchenette", Item.Category.APPLIANCE, "Fridge");

        LocalDate today = LocalDate.now();
        LocalDate start = today.minusMonths(2).withDayOfMonth(1);
        Agreement agreement = lease.createAgreement(maria, apartment, andrei, start, start.plusMonths(12).minusDays(1),
                1800, 5, 1800, EnumSet.of(BillType.INTERNET), "No smoking inside. Quiet hours 22:00-07:00.");
        lease.signAgreement(andrei, agreement);
        lease.payDeposit(andrei, agreement, Payment.Method.CARD, start);

        Inspection moveIn = lease.startInspection(andrei, agreement, Inspection.Type.MOVE_IN);
        for (Item item : apartment.getItems()) {
            moveIn.record(switch (item.getName()) {
                case "Sofa" -> new InspectionEntry(item.getId(), Condition.WORN, "Scratch on the left armrest", null,
                        photo(vault, samples, "sofa-scratch"));
                case "Mirror" -> new InspectionEntry(item.getId(), Condition.DAMAGED, "Crack in the lower right corner", null,
                        photo(vault, samples, "mirror-crack"));
                case "Water meter" -> new InspectionEntry(item.getId(), Condition.GOOD, null, 123.4, photo(vault, samples, "water-meter"));
                case "Electricity meter" -> new InspectionEntry(item.getId(), Condition.GOOD, null, 5531.0, photo(vault, samples, "electricity-meter"));
                case "Gas meter" -> new InspectionEntry(item.getId(), Condition.GOOD, null, 812.7, photo(vault, samples, "gas-meter"));
                case "Kitchen table" -> new InspectionEntry(item.getId(), Condition.GOOD, null, null, photo(vault, samples, "room-overview"));
                default -> new InspectionEntry(item.getId(), Condition.GOOD, null, null, null);
            });
        }
        lease.submitInspection(moveIn);
        lease.confirmInspection(maria, moveIn);

        lease.generateRentInvoices(agreement, YearMonth.from(today).minusMonths(1));
        lease.uploadBill(maria, agreement, BillType.WATER, YearMonth.from(start), 87.40, photo(vault, samples, "water-bill"));
        lease.payStatement(andrei, agreement, Payment.Method.BANK_TRANSFER, today.minusDays(10));
        lease.uploadBill(maria, agreement, BillType.ELECTRICITY, YearMonth.from(today).minusMonths(1), 143.25,
                photo(vault, samples, "electricity-bill"));

        lease.sendMessage(andrei, agreement, "Hi! I finished the move-in photos. The sofa scratch and the mirror crack were already there.");
        lease.sendMessage(maria, agreement, "Thanks Andrei, I confirmed the report. Last month's electricity bill is uploaded.");
    }

    private static void addItems(FairLease lease, Property property, String room, Item.Category category, String... names)
            throws AgreementStateException {
        for (String name : names) {
            lease.addItem(property, room, name, category);
        }
    }

    private static Photo photo(PhotoVault vault, Path samples, String name) throws IOException, InvalidInputException {
        return vault.importPhoto(samples.resolve(name + ".png").toString(), LocalDateTime.now());
    }

    private static void drawPicture(Path file, String caption) throws IOException {
        BufferedImage image = new BufferedImage(640, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(235, 240, 245));
        g.fillRect(0, 0, 640, 400);
        g.setColor(new Color(40, 90, 140));
        g.fillRect(0, 0, 640, 60);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
        g.drawString("FairLease evidence photo", 20, 40);
        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 24));
        g.drawString(caption, 20, 210);
        g.dispose();
        ImageIO.write(image, "png", file.toFile());
    }
}
