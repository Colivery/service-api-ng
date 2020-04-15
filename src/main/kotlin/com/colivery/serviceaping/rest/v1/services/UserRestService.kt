package com.colivery.serviceaping.rest.v1.services

import com.colivery.serviceaping.dto.UserOrderAcceptedResponse
import com.colivery.serviceaping.dto.UserOrderResponse
import com.colivery.serviceaping.extensions.getUser
import com.colivery.serviceaping.extensions.toGeoPoint
import com.colivery.serviceaping.mapping.toOrderResource
import com.colivery.serviceaping.mapping.toUserResource
import com.colivery.serviceaping.persistence.entity.UserEntity
import com.colivery.serviceaping.persistence.repository.OrderRepository
import com.colivery.serviceaping.persistence.repository.UserRepository
import com.colivery.serviceaping.rest.v1.dto.user.CreateUserDto
import com.colivery.serviceaping.rest.v1.resources.UserResource
import com.colivery.serviceaping.util.extractBearerToken
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import org.locationtech.jts.geom.GeometryFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.context.request.ServletWebRequest
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import javax.transaction.Transactional

@RestController
@Transactional
@RequestMapping("/v1/user", produces = [MediaType.APPLICATION_JSON_VALUE])
class UserRestService(
        private val orderRepository: OrderRepository,
        private val userRepository: UserRepository,
        private val firebaseAuth: FirebaseAuth,
        private val geometryFactory: GeometryFactory
) {

    @GetMapping("/orders")
    fun getOrdersForUser(authentication: Authentication): Flux<UserOrderResponse> {
        val user = authentication.getUser()

        return Flux.fromIterable(this.orderRepository.findAllByUser(user)
                .map { order ->
                    UserOrderResponse(
                            toOrderResource(order),
                            order.driverUser?.let { toUserResource(it) }
                    )
                }
        )
    }

    @GetMapping("/orders-accepted")
    fun getDriverOrders(authentication: Authentication): Flux<UserOrderAcceptedResponse> {
        val user = authentication.getUser()

        return Flux.fromIterable(this.orderRepository.findAllByDriverUser(user)
                .map { order ->
                    UserOrderAcceptedResponse(
                            toOrderResource(order),
                            toUserResource(order.user)
                    )
                }
        )
    }

    @DeleteMapping
    fun deleteOwnUser() {

    }

    @PostMapping
    fun createUser(@RequestBody createUserDto: CreateUserDto, request: ServletWebRequest):
            ResponseEntity<Mono<UserResource>> {
        val unauthorized = ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .build<Mono<UserResource>>()

        // Get the bearer token
        val token = extractBearerToken(request)
                ?: return unauthorized

        try {
            val firebaseToken = this.firebaseAuth.verifyIdToken(token)
            // First, make sure that the user wasnt created before
            if (this.userRepository.existsByFirebaseUid(firebaseToken.uid)) {
                return ResponseEntity.badRequest()
                        .build()
            }

            val user = this.userRepository.save(UserEntity(
                    firstName = createUserDto.firstName,
                    lastName = createUserDto.lastName,
                    street = createUserDto.street,
                    streetNo = createUserDto.streetNo,
                    zipCode = createUserDto.zipCode,
                    city = createUserDto.city,
                    email = createUserDto.email,
                    firebaseUid = firebaseToken.uid,
                    location = createUserDto.location.toGeoPoint(this.geometryFactory),
                    locationGeoHash = createUserDto.locationGeoHash,
                    phone = createUserDto.phone
            ))

            return ResponseEntity.ok(Mono.just(toUserResource(user)))
        } catch (exception: FirebaseAuthException) {
            return unauthorized
        }
    }

    @GetMapping
    fun getUser(authentication: Authentication) =
            Mono.just(toUserResource(authentication.getUser()))

}
