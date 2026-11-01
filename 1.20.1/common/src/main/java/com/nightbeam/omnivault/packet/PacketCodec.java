package com.nightbeam.omnivault.packet;

import net.minecraft.network.FriendlyByteBuf;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Writes a value to a packet buffer and reads it back, in the style of the stream codecs of later
 * Minecraft versions, so payloads and the values they carry declare their wire format in one place.
 */
public interface PacketCodec<T> {
    T decode(FriendlyByteBuf buf);

    void encode(FriendlyByteBuf buf, T value);

    static <T> PacketCodec<T> of(BiConsumer<FriendlyByteBuf, T> encoder, Function<FriendlyByteBuf, T> decoder) {
        return new PacketCodec<>() {
            @Override
            public T decode(FriendlyByteBuf buf) {
                return decoder.apply(buf);
            }

            @Override
            public void encode(FriendlyByteBuf buf, T value) {
                encoder.accept(buf, value);
            }
        };
    }

    /** A value that is always the same and takes no bytes. */
    static <T> PacketCodec<T> unit(T value) {
        return of((buf, v) -> {
        }, buf -> value);
    }

    default <O> PacketCodec<O> map(Function<? super T, ? extends O> to, Function<? super O, ? extends T> from) {
        PacketCodec<T> self = this;
        return of((buf, value) -> self.encode(buf, from.apply(value)), buf -> to.apply(self.decode(buf)));
    }

    /** Wraps this codec, for example in {@link PacketCodecs#list()}. */
    default <O> PacketCodec<O> apply(Function<PacketCodec<T>, PacketCodec<O>> operation) {
        return operation.apply(this);
    }

    static <C, T1> PacketCodec<C> composite(PacketCodec<T1> codec1, Function<C, T1> getter1, Function<T1, C> factory) {
        return of((buf, value) -> codec1.encode(buf, getter1.apply(value)), buf -> factory.apply(codec1.decode(buf)));
    }

    static <C, T1, T2> PacketCodec<C> composite(PacketCodec<T1> codec1, Function<C, T1> getter1,
                                                PacketCodec<T2> codec2, Function<C, T2> getter2,
                                                Function2<T1, T2, C> factory) {
        return of((buf, value) -> {
            codec1.encode(buf, getter1.apply(value));
            codec2.encode(buf, getter2.apply(value));
        }, buf -> factory.apply(codec1.decode(buf), codec2.decode(buf)));
    }

    static <C, T1, T2, T3> PacketCodec<C> composite(PacketCodec<T1> codec1, Function<C, T1> getter1,
                                                    PacketCodec<T2> codec2, Function<C, T2> getter2,
                                                    PacketCodec<T3> codec3, Function<C, T3> getter3,
                                                    Function3<T1, T2, T3, C> factory) {
        return of((buf, value) -> {
            codec1.encode(buf, getter1.apply(value));
            codec2.encode(buf, getter2.apply(value));
            codec3.encode(buf, getter3.apply(value));
        }, buf -> factory.apply(codec1.decode(buf), codec2.decode(buf), codec3.decode(buf)));
    }

    static <C, T1, T2, T3, T4> PacketCodec<C> composite(PacketCodec<T1> codec1, Function<C, T1> getter1,
                                                        PacketCodec<T2> codec2, Function<C, T2> getter2,
                                                        PacketCodec<T3> codec3, Function<C, T3> getter3,
                                                        PacketCodec<T4> codec4, Function<C, T4> getter4,
                                                        Function4<T1, T2, T3, T4, C> factory) {
        return of((buf, value) -> {
            codec1.encode(buf, getter1.apply(value));
            codec2.encode(buf, getter2.apply(value));
            codec3.encode(buf, getter3.apply(value));
            codec4.encode(buf, getter4.apply(value));
        }, buf -> factory.apply(codec1.decode(buf), codec2.decode(buf), codec3.decode(buf), codec4.decode(buf)));
    }

    static <C, T1, T2, T3, T4, T5> PacketCodec<C> composite(PacketCodec<T1> codec1, Function<C, T1> getter1,
                                                            PacketCodec<T2> codec2, Function<C, T2> getter2,
                                                            PacketCodec<T3> codec3, Function<C, T3> getter3,
                                                            PacketCodec<T4> codec4, Function<C, T4> getter4,
                                                            PacketCodec<T5> codec5, Function<C, T5> getter5,
                                                            Function5<T1, T2, T3, T4, T5, C> factory) {
        return of((buf, value) -> {
            codec1.encode(buf, getter1.apply(value));
            codec2.encode(buf, getter2.apply(value));
            codec3.encode(buf, getter3.apply(value));
            codec4.encode(buf, getter4.apply(value));
            codec5.encode(buf, getter5.apply(value));
        }, buf -> factory.apply(codec1.decode(buf), codec2.decode(buf), codec3.decode(buf), codec4.decode(buf), codec5.decode(buf)));
    }

    static <C, T1, T2, T3, T4, T5, T6> PacketCodec<C> composite(PacketCodec<T1> codec1, Function<C, T1> getter1,
                                                                PacketCodec<T2> codec2, Function<C, T2> getter2,
                                                                PacketCodec<T3> codec3, Function<C, T3> getter3,
                                                                PacketCodec<T4> codec4, Function<C, T4> getter4,
                                                                PacketCodec<T5> codec5, Function<C, T5> getter5,
                                                                PacketCodec<T6> codec6, Function<C, T6> getter6,
                                                                Function6<T1, T2, T3, T4, T5, T6, C> factory) {
        return of((buf, value) -> {
            codec1.encode(buf, getter1.apply(value));
            codec2.encode(buf, getter2.apply(value));
            codec3.encode(buf, getter3.apply(value));
            codec4.encode(buf, getter4.apply(value));
            codec5.encode(buf, getter5.apply(value));
            codec6.encode(buf, getter6.apply(value));
        }, buf -> factory.apply(codec1.decode(buf), codec2.decode(buf), codec3.decode(buf), codec4.decode(buf), codec5.decode(buf),
                codec6.decode(buf)));
    }

    @FunctionalInterface
    interface Function2<A, B, R> {
        R apply(A a, B b);
    }

    @FunctionalInterface
    interface Function3<A, B, C, R> {
        R apply(A a, B b, C c);
    }

    @FunctionalInterface
    interface Function4<A, B, C, D, R> {
        R apply(A a, B b, C c, D d);
    }

    @FunctionalInterface
    interface Function5<A, B, C, D, E, R> {
        R apply(A a, B b, C c, D d, E e);
    }

    @FunctionalInterface
    interface Function6<A, B, C, D, E, F, R> {
        R apply(A a, B b, C c, D d, E e, F f);
    }
}
