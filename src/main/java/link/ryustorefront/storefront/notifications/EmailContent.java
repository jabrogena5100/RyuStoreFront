package link.ryustorefront.storefront.notifications;

/** A finished email: subject plus a plain-text and an HTML body (sent together as multipart/alternative). */
public record EmailContent(String subject, String text, String html) {
}
