package fr.epita.platform.user.service;

import fr.epita.platform.user.entity.User;

/**
 * Contrôle supplémentaire avant la suppression ou la désactivation d'un utilisateur.
 * Une mini-application peut en fournir un (ex. : inventaire, {@code USER_HOLDS_EQUIPMENT}).
 * L'implémentation lève une {@link fr.epita.platform.common.ApiException} pour refuser l'action.
 */
public interface UserDeletionGuard {

    void beforeRemoval(User user);
}
