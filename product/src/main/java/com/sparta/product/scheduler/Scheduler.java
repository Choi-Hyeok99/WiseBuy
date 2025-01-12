package com.sparta.product.scheduler;

import com.sparta.product.entitiy.Product;
import com.sparta.product.entitiy.ProductType;
import com.sparta.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class Scheduler {

    private final ProductRepository productRepository;


    // 1분 간격으로 실행
    @Scheduled(fixedRate = 60000)
    public void updateProductStatus(){

        List<Product> products = productRepository.findAll();

        for (Product product : products){
            if (product.getProductType() == ProductType.FLASH_SALE){
                // 선착순 상품 로직
                product.updateStatus();
            } else if (product.getProductType() == ProductType.GENERAL){
                // 일반 상품은 상태 업데이트 필요 없음
                continue;
            }
        }
        productRepository.saveAll(products);
    }
}
