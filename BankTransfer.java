import java.sql.*;
import java.util.Scanner;

public class BankTransfer {

    public static void main(String[] args) {

        String url = "jdbc:mysql://localhost:3306/college";
        String user = "root";
        String password = "YOUR_MYSQL_PASSWORD";

        Scanner sc = new Scanner(System.in);

        System.out.print("Enter Sender Account Number: ");
        int sender = sc.nextInt();

        System.out.print("Enter Receiver Account Number: ");
        int receiver = sc.nextInt();

        System.out.print("Enter Amount to Transfer: ");
        double amount = sc.nextDouble();

        if (sender == receiver) {
            System.out.println("Error: Sender and receiver must be different.");
            sc.close();
            return;
        }

        if (amount <= 0 || !Double.isFinite(amount)) {
            System.out.println("Error: Amount must be greater than zero.");
            sc.close();
            return;
        }

        try (Connection con =
                 DriverManager.getConnection(url, user, password)) {

            // Start transaction
            con.setAutoCommit(false);

            try {
                // Lock both accounts and verify they exist
                String checkSql =
                    "SELECT account_no, balance FROM bank_accounts " +
                    "WHERE account_no IN (?, ?) " +
                    "ORDER BY account_no FOR UPDATE";

                boolean senderFound = false;
                boolean receiverFound = false;
                double senderBalance = 0;

                try (PreparedStatement ps =
                         con.prepareStatement(checkSql)) {

                    ps.setInt(1, sender);
                    ps.setInt(2, receiver);

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            int accountNo = rs.getInt("account_no");

                            if (accountNo == sender) {
                                senderFound = true;
                                senderBalance = rs.getDouble("balance");
                            }

                            if (accountNo == receiver) {
                                receiverFound = true;
                            }
                        }
                    }
                }

                if (!senderFound || !receiverFound) {
                    throw new SQLException(
                        "Sender or receiver account does not exist."
                    );
                }

                if (senderBalance < amount) {
                    throw new SQLException("Insufficient balance.");
                }

                // Debit sender
                String debitSql =
                    "UPDATE bank_accounts " +
                    "SET balance = balance - ? " +
                    "WHERE account_no = ? AND balance >= ?";

                try (PreparedStatement ps =
                         con.prepareStatement(debitSql)) {

                    ps.setDouble(1, amount);
                    ps.setInt(2, sender);
                    ps.setDouble(3, amount);

                    if (ps.executeUpdate() != 1) {
                        throw new SQLException("Debit failed.");
                    }
                }

                // Credit receiver
                String creditSql =
                    "UPDATE bank_accounts " +
                    "SET balance = balance + ? " +
                    "WHERE account_no = ?";

                try (PreparedStatement ps =
                         con.prepareStatement(creditSql)) {

                    ps.setDouble(1, amount);
                    ps.setInt(2, receiver);

                    if (ps.executeUpdate() != 1) {
                        throw new SQLException("Credit failed.");
                    }
                }

                // Save both changes permanently
                con.commit();

                System.out.println("Transfer Successful!");
                System.out.println("Amount Transferred: " + amount);

            } catch (Exception e) {

                // Cancel both debit and credit if anything fails
                con.rollback();

                System.out.println("Transfer Failed!");
                System.out.println("Reason: " + e.getMessage());
                System.out.println("Transaction rolled back.");

            } finally {
                con.setAutoCommit(true);
            }

        } catch (SQLException e) {
            System.out.println("Database Error: " + e.getMessage());
        }

        sc.close();
    }
}
