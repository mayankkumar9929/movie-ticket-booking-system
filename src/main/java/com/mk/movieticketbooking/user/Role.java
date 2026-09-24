package com.mk.movieticketbooking.user;

/**
 * The two roles supported by the system, per problem statement:
 * <ul>
 *   <li><b>ADMIN</b>: manages cities, theaters, shows, seat layouts, pricing tiers,
 *       and refund policies.</li>
 *   <li><b>CUSTOMER</b>: browses shows, books and cancels seats, views booking history.</li>
 * </ul>
 * Spring Security expects role authorities to be prefixed with {@code ROLE_};
 * we store the bare enum name and add the prefix at authority-mapping time.
 */
public enum Role {
  ADMIN,
  CUSTOMER
}
